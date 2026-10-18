package com.bbh.itss.beadle.protech.adapter.in.web;

import com.bbh.itss.beadle.protech.adapter.BeadleHeaders;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.bbh.itss.beadle.protech.domain.calllog.CallLogEntry.CORRELATION_MAX;
import static com.bbh.itss.beadle.protech.domain.shared.Text.bytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

/**
 * Lets a call in only with the Beadle service token ({@code beadle.services.token}, {@code BEADLE_SERVICE_TOKEN}) as
 * {@code Authorization: Bearer <token>}, or, for {@code POST /api/protech/webhook} alone, with the token ProTech holds
 * for it ({@code beadle.protech.webhook-token}, {@code PROTECH_WEBHOOK_TOKEN}), so that the token of the Beadle
 * services never leaves the cluster. Anything else is answered {@code 401} with a problem; the health endpoints under
 * {@code /actuator/health}, spelled plainly, are open to the probes of OpenShift, and a blank token opens nothing. A
 * call let in runs as the caller its headers name ({@code X-Beadle-User}, percent-decoded, {@link Caller#UNKNOWN}
 * when there is none, and {@code X-Correlation-Id}, a fresh one when there is none and {@code 400} when it is longer
 * than 64 characters), so that the call log can tell who caused each call to
 * ProTech; the correlation id is in the MDC as {@value #MDC_KEY} for every line logged meanwhile.
 */
@Slf4j
@Component
@Order(HIGHEST_PRECEDENCE)
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String OPEN_PATH = "/actuator/health";
    static final Pattern OPEN = Pattern.compile(Pattern.quote(OPEN_PATH) + "(/[A-Za-z0-9_-]+)*");
    static final String WEBHOOK_PATH = "/api/protech/webhook";
    public static final String BEARER = "Bearer ";
    public static final String DETAIL = "The call needs the Beadle service token as Authorization: Bearer <token>.";
    public static final String CORRELATION_TOO_LONG = "is too long: it may take at most " + CORRELATION_MAX
            + " characters";
    static final String UNAUTHORIZED_PROBLEM = "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + DETAIL
            + "\"}";
    static final String CORRELATION_PROBLEM = "{\"title\":\"Validation failed\",\"status\":400,\"detail\":\""
            + BeadleHeaders.CORRELATION_ID + " " + CORRELATION_TOO_LONG + "\",\"problems\":[{\"field\":\""
            + BeadleHeaders.CORRELATION_ID + "\",\"message\":\"" + CORRELATION_TOO_LONG + "\"}]}";
    public static final String MDC_KEY = "correlationId";

    private final byte[] token;
    private final byte[] webhookToken;

    public ServiceTokenFilter(@Value("${beadle.services.token:}") String token,
                              @Value("${beadle.protech.webhook-token:}") String webhookToken) {
        this.token = trimToEmpty(token).getBytes(UTF_8);
        this.webhookToken = trimToEmpty(webhookToken).getBytes(UTF_8);
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
        if (!accepts(request)) {
            log.warn("Refused {} {}: missing or wrong service token", request.getMethod(), request.getRequestURI());
            response.setHeader(WWW_AUTHENTICATE, "Bearer");
            problem(response, UNAUTHORIZED, UNAUTHORIZED_PROBLEM);
            return;
        }
        String correlationId = trimToNull(request.getHeader(BeadleHeaders.CORRELATION_ID));
        if (correlationId != null && bytes(correlationId) > CORRELATION_MAX) {
            problem(response, BAD_REQUEST, CORRELATION_PROBLEM);
            return;
        }
        Caller caller = new Caller(userOf(request.getHeader(BeadleHeaders.USER)),
                correlationId == null ? UUID.randomUUID().toString() : correlationId);
        CallerContext.set(caller);
        MDC.put(MDC_KEY, caller.correlationId());
        try {
            chain.doFilter(request, response);
        } finally {
            CallerContext.clear();
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * The user {@code X-Beadle-User} names. A header carries ASCII only, so the contract has the name percent-encoded
     * in UTF-8 as {@link java.net.URLEncoder} writes it ({@code Łukasz Wójcik} travels as
     * {@code %C5%81ukasz+W%C3%B3jcik}); a plain ASCII name reads the same either way, and a value that is not valid
     * percent-encoding is taken as it came.
     */
    static String userOf(String header) {
        String value = trimToEmpty(header);
        try {
            value = trimToEmpty(URLDecoder.decode(value, UTF_8));
        } catch (IllegalArgumentException notEncoded) {
            // taken as it came
        }
        return value.isEmpty() ? Caller.UNKNOWN : value;
    }

    /**
     * Whether the call carries the service token, or is the webhook carrying its own token: matched on the raw
     * request URI, so that {@code /api/protech/webhook/../changes} is not taken for it.
     */
    boolean accepts(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return false;
        }
        byte[] presented = authorization.substring(BEARER.length()).trim().getBytes(UTF_8);
        boolean webhook = "POST".equals(request.getMethod())
                && (request.getContextPath() + WEBHOOK_PATH).equals(request.getRequestURI());
        return matches(token, presented) || webhook && matches(webhookToken, presented);
    }

    private static boolean matches(byte[] expected, byte[] presented) {
        return expected.length > 0 && isEqual(expected, presented);
    }

    private static void problem(HttpServletResponse response, HttpStatus status, String problem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(problem);
    }
}
