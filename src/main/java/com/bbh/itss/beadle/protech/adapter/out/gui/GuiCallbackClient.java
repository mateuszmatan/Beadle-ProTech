package com.bbh.itss.beadle.protech.adapter.out.gui;

import com.bbh.itss.beadle.protech.adapter.BeadleHeaders;
import com.bbh.itss.beadle.protech.application.callback.port.out.CallbackPort;
import com.bbh.itss.beadle.protech.domain.calllog.Caller;
import com.bbh.itss.beadle.protech.domain.calllog.CallerContext;
import com.bbh.itss.beadle.protech.domain.change.ChangeCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.apache.commons.lang3.Strings.CS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Tells Beadle-GUI that ProTech moved a change it knows ({@code POST /api/callbacks/protech}), with the service token
 * every call between the Beadle services carries, {@code system} as the user and, in {@code X-Correlation-Id} only,
 * the correlation id of the user action the change goes back to (the caller of the current thread); Beadle-GUI answers
 * {@code 202}. Anything else is thrown, and the change is told about again at the next look.
 */
@Component
class GuiCallbackClient implements CallbackPort {

    static final String GUI = "Beadle-GUI";
    static final String PATH = "/api/callbacks/protech";
    static final int MAX_REASON = 300;

    private final RestClient client;

    GuiCallbackClient(GuiProperties properties, @Value("${beadle.services.token:}") String token,
                      RestClient.Builder builder) {
        this.client = builder.baseUrl(CS.removeEnd(trimToEmpty(properties.url()), "/"))
                .defaultHeader(AUTHORIZATION, "Bearer " + trimToEmpty(token)).defaultHeader(BeadleHeaders.USER, Caller.SYSTEM)
                .build();
    }

    @Override
    public void notify(ChangeCallback callback) {
        try {
            ResponseEntity<Void> answer = client.post().uri(PATH).contentType(APPLICATION_JSON).accept(APPLICATION_JSON)
                    .headers(headers -> Optional.ofNullable(CallerContext.current().correlationId())
                            .ifPresent(id -> headers.set(BeadleHeaders.CORRELATION_ID, id)))
                    .body(callback).retrieve().toBodilessEntity();
            if (!answer.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException(GUI + " answered " + answer.getStatusCode());
            }
        } catch (RestClientException e) {
            String reason = defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            throw new UncheckedIOException(GUI + " could not take the callback for " + callback.number() + ": "
                    + truncate(reason, MAX_REASON), new IOException(e));
        }
    }
}
