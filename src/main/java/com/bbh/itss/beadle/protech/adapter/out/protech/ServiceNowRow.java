package com.bbh.itss.beadle.protech.adapter.out.protech;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Map.Entry;

import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * One record of a Table API answer. A field is either a plain text ({@code sysparm_display_value=true}) or an
 * object with its {@code value} and {@code display_value} ({@code sysparm_display_value=all}). Date-time values are
 * in the internal form of ServiceNow, {@code yyyy-MM-dd HH:mm:ss} in UTC.
 */
record ServiceNowRow(Map<String, Object> fields) {

    static final String SYS_ID = "sys_id";
    static final String UPDATED = "sys_updated_on";
    static final DateTimeFormatter DATE_TIME = ofPattern("yyyy-MM-dd HH:mm:ss").withZone(UTC);

    ServiceNowRow {
        fields = fields.entrySet().stream().filter(field -> field.getValue() != null)
                .collect(toUnmodifiableMap(Entry::getKey, Entry::getValue));
    }

    static String dateTime(Instant at) {
        return at == null ? null : DATE_TIME.format(at);
    }

    static Instant instantOf(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME).toInstant(UTC);
        } catch (DateTimeParseException e) {
            return Instant.parse(value);
        }
    }

    String sysId() {
        return value(SYS_ID);
    }

    /** Whether the answer holds the field at all: a field ProTech does not have is left out of its answer. */
    boolean has(String field) {
        return !field.isEmpty() && fields.containsKey(field);
    }

    String value(String field) {
        return part(field, "value");
    }

    String display(String field) {
        return part(field, "display_value");
    }

    /** The display value of the field, or {@code fallback} when the field is not mapped or not in the answer. */
    String text(String field, String fallback) {
        return has(field) ? display(field) : fallback;
    }

    /** The date-time of the field, or {@code fallback} when the field is not mapped or not in the answer. */
    Instant instant(String field, Instant fallback) {
        return has(field) ? instantOf(value(field)) : fallback;
    }

    private String part(String field, String part) {
        Object found = field.isEmpty() ? null : fields.get(field);
        if (found instanceof Map<?, ?> parts) {
            Object chosen = parts.get(part);
            return chosen == null ? null : trimToNull(String.valueOf(chosen));
        }
        return found == null ? null : trimToNull(String.valueOf(found));
    }
}
