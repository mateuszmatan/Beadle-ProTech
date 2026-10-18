package com.bbh.itss.beadle.protech.adapter.out.protech;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * One record of a Table API answer. A field is either a plain text ({@code sysparm_display_value=true}) or an
 * object with its {@code value} and {@code display_value} ({@code sysparm_display_value=all}). Date-time values are
 * in the internal form of ServiceNow, {@code yyyy-MM-dd HH:mm:ss} in UTC; one that is no date-time is read as none,
 * with a warning the first time it comes.
 */
@Slf4j
record TableRow(Map<String, Object> fields) {

    static final String SYS_ID = "sys_id";
    static final String UPDATED = "sys_updated_on";
    static final DateTimeFormatter DATE_TIME = ofPattern("yyyy-MM-dd HH:mm:ss").withZone(UTC);
    private static final Set<String> UNREADABLE = ConcurrentHashMap.newKeySet();

    TableRow {
        fields = fields.entrySet().stream().filter(field -> field.getValue() != null)
                .collect(toUnmodifiableMap(Entry::getKey, Entry::getValue));
    }

    static String dateTime(Instant at) {
        return at == null ? null : DATE_TIME.format(at);
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

    /** The date-time of the field, or {@code fallback} when it is not mapped, not in the answer or no date-time. */
    Instant instant(String field, Instant fallback) {
        Instant at = has(field) ? instantOf(field, value(field)) : null;
        return at == null ? fallback : at;
    }

    private static Instant instantOf(String field, String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME).toInstant(UTC);
        } catch (DateTimeParseException internal) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException unreadable) {
                if (UNREADABLE.add(value)) {
                    log.warn("ProTech answered {} for {}, which is no date-time: it is read as none", value, field);
                }
                return null;
            }
        }
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
