package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.domain.shared.RefusedException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.apache.commons.lang3.Strings.CS;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * The Table API of ServiceNow ({@code /api/now/table/{table}}) as ProTech offers it, signed in with basic
 * authentication as the Beadle service account. Reads answer every field with its value and its display value
 * ({@code sysparm_display_value=all}); writes give the display values ({@code sysparm_input_display_value=true}), so
 * that a person, a group or a configuration item is named as Beadle shows it. A refusal of ProTech (a {@code 4xx}
 * other than a failed sign-in) becomes an {@link RefusedException} with ProTech's message; ProTech out of reach,
 * a failed sign-in or a {@code 5xx} answer becomes an {@link UncheckedIOException}.
 */
class TableApi {

    static final String PROTECH = "ProTech";
    static final String TABLE = "/api/now/table/{table}";
    static final int MAX_REASON = 300;
    private static final Set<Integer> UNAVAILABLE = Set.of(401, 403, 407, 408, 429);
    private static final ParameterizedTypeReference<Result<List<Map<String, Object>>>> ROWS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<Map<String, Object>>> ROW =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;

    TableApi(RestClient.Builder builder, ProTechProperties properties) {
        this.client = builder.baseUrl(CS.removeEnd(trimToEmpty(properties.instanceUrl()), "/"))
                .defaultHeaders(headers -> headers.setBasicAuth(defaultString(properties.username()),
                        defaultString(properties.password()), UTF_8))
                .build();
    }

    /** The records of a table that match an encoded query, each field with its value and its display value. */
    List<TableRow> list(String table, String query, Collection<String> fields, int limit, String operation) {
        return rows(table, query, fields, limit, "all", operation);
    }

    /** The records of a table that match an encoded query, each field with its display value only. */
    List<TableRow> search(String table, String query, Collection<String> fields, int limit, String operation) {
        return rows(table, query, fields, limit, "true", operation);
    }

    /** Inserts a record given by display values and answers the requested fields of the new record. */
    TableRow create(String table, Map<String, Object> body, Collection<String> fields, String operation) {
        try {
            Result<Map<String, Object>> created = client.post()
                    .uri(TABLE + "?sysparm_input_display_value=true&sysparm_fields={fields}"
                            + "&sysparm_exclude_reference_link=true", table, String.join(",", fields))
                    .contentType(APPLICATION_JSON).accept(APPLICATION_JSON).body(body).retrieve().body(ROW);
            return new TableRow(Optional.ofNullable(created).map(Result::result).orElse(Map.of()));
        } catch (RestClientException e) {
            throw refusal(operation, e);
        }
    }

    /** Changes the fields of a record, given by display values or, with {@code displayValues} off, by values. */
    void update(String table, String sysId, Map<String, Object> body, boolean displayValues, String operation) {
        try {
            client.patch().uri(TABLE + "/{sysId}?sysparm_input_display_value={display}&sysparm_fields=sys_id",
                            table, sysId, displayValues)
                    .contentType(APPLICATION_JSON).accept(APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw refusal(operation, e);
        }
    }

    private List<TableRow> rows(String table, String query, Collection<String> fields, int limit,
                                     String display, String operation) {
        try {
            Result<List<Map<String, Object>>> found = client.get()
                    .uri(TABLE + "?sysparm_query={query}&sysparm_fields={fields}&sysparm_limit={limit}"
                                    + "&sysparm_display_value={display}&sysparm_exclude_reference_link=true",
                            table, query, String.join(",", fields), limit, display)
                    .accept(APPLICATION_JSON).retrieve().body(ROWS);
            return emptyIfNull(found == null ? null : found.result()).stream().map(TableRow::new).toList();
        } catch (RestClientException e) {
            throw refusal(operation, e);
        }
    }

    static RuntimeException refusal(String operation, RestClientException cause) {
        if (cause instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()
                && !UNAVAILABLE.contains(response.getStatusCode().value())) {
            return new RefusedException(PROTECH + " refused to " + operation + ": " + messageOf(response));
        }
        String reason = cause instanceof RestClientResponseException response
                ? response.getStatusCode().value() + " " + messageOf(response)
                : defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName());
        return new UncheckedIOException(PROTECH + " could not " + operation + ": " + truncate(reason, MAX_REASON),
                new IOException(cause));
    }

    private static String messageOf(RestClientResponseException response) {
        String message;
        try {
            Failure failure = response.getResponseBodyAs(Failure.class);
            Failure.Error error = failure == null ? null : failure.error();
            message = error == null || !isNotBlank(error.message()) ? null
                    : error.message() + (isNotBlank(error.detail()) ? " (" + error.detail() + ")" : "");
        } catch (RuntimeException unreadable) {
            message = null;
        }
        return truncate(defaultIfBlank(message, defaultIfBlank(response.getStatusText(), "no reason given")),
                MAX_REASON);
    }

    record Result<T>(T result) {
    }

    record Failure(Error error, String status) {

        record Error(String message, String detail) {
        }
    }
}
