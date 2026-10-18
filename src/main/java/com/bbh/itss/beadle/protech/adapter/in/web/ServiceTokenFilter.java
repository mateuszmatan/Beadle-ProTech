package com.bbh.itss.beadle.protech.adapter.in.web;

import com.bbh.itss.beadle.protech.application.calllog.CallerContext;
import com.bbh.itss.beadle.protech.domain.calllog.Caller;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.bbh.itss.beadle.protech.application.calllog.CallerContext.CORRELATION_HEADER;
import static com.bbh.itss.beadle.protech.application.calllog.CallerContext.USER_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

/**
 * Lets a call in only with the Beadle service token ({@code beadle.services.token}, {@code BEADLE_SERVICE_TOKEN}) as
 * {@code Authorization: Bearer <token>} and answers anything else {@code 401} with a problem; the health endpoints
 * under {@code /actuator/health}, spelled plainly, are open to the probes of OpenShift. A blank token refuses every
 * call. A call let in runs as the caller its headers name ({@code X-Beadle-User}, percent-decoded, and
 * {@code X-Correlation-Id}), with a fresh correlation id when Beadle-GUI sent none, so that the call log can tell who
 * caused each call to ProTech; the correlation id is in the MDC as {@value #MDC_KEY} for every line logged meanwhile.
 */
@Slf4j
@Component
@Order(HIGHEST_PRECEDENCE)
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String OPEN_PATH = "/actuator/health";
    static final Pattern OPEN = Pattern.compile(Pattern.quote(OPEN_PATH) + "(/[A-Za-z0-9_-]+)*");
    public static final String BEARER = "Bearer ";
    public static final String DETAIL = "The call needs the Beadle service token as Authorization: Bearer <token>.";
    static final String PROBLEM = "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + DETAIL + "\"}";
    public static final String MDC_KEY = "correlationId";

    private final byte[] token;

    public ServiceTokenFilter(@Value("${beadle.services.token:}") String token) {
        this.token = trimToEmpty(token).getBytes(UTF_8);
        if (this.token.length == 0) {
            log.warn("beadle.services.token (BEADLE_SERVICE_TOKEN) is blank: every call of the API is refused");
        }
    }

    /**
     * Only the health endpoints spelled plainly are open: the request URI as sent, without a dot segment, a path
     * parameter, an encoded character or an empty segment. Such a URI could otherwise start with {@value #OPEN_PATH}
     * and still lead the dispatcher somewhere else ({@code /actuator/health/../../api/protech/status}), so it needs
     * the token like any other call.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return uri != null && uri.startsWith(context) && OPEN.matcher(uri.substring(context.length())).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (accepts(request.getHeader(AUTHORIZATION))) {
            Caller caller = callerOf(request);
            CallerContext.set(caller);
            MDC.put(MDC_KEY, caller.correlationId());
            try {
                chain.doFilter(request, response);
            } finally {
                CallerContext.clear();
                MDC.remove(MDC_KEY);
            }
            return;
        }
        response.setStatus(UNAUTHORIZED.value());
        response.setHeader(WWW_AUTHENTICATE, "Bearer");
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(PROBLEM);
    }

    static Caller callerOf(HttpServletRequest request) {
        String correlationId = trimToNull(request.getHeader(CORRELATION_HEADER));
        return new Caller(userOf(request.getHeader(USER_HEADER)),
                correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }

    /**
     * The user {@code X-Beadle-User} names. A header carries ASCII only, so the contract has the name percent-encoded
     * in UTF-8 as {@link java.net.URLEncoder} writes it ({@code Łukasz Wójcik} travels as
     * {@code %C5%81ukasz+W%C3%B3jcik}); a plain ASCII name reads the same either way, and a value that is not valid
     * percent-encoding is taken as it came.
     */
    static String userOf(String header) {
        String value = trimToNull(header);
        if (value == null) {
            return null;
        }
        try {
            return trimToNull(URLDecoder.decode(value, UTF_8));
        } catch (IllegalArgumentException notEncoded) {
            return value;
        }
    }

    boolean accepts(String authorization) {
        if (token.length == 0 || isBlank(authorization) || !authorization.startsWith(BEARER)) {
            return false;
        }
        return isEqual(token, authorization.substring(BEARER.length()).trim().getBytes(UTF_8));
    }
}
