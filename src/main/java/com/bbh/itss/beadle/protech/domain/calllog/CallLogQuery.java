package com.bbh.itss.beadle.protech.domain.calllog;

import com.bbh.itss.beadle.protech.domain.shared.ValidationProblems;

import java.time.Instant;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/** A search of the call log: by correlation id, from (inclusive) and to (exclusive), page by page. */
public record CallLogQuery(String correlationId, Instant from, Instant to, int page, int size) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;
    public static final String PAGE_MESSAGE = "must be 0 or more";
    public static final String SIZE_MESSAGE = "must be between 1 and " + MAX_SIZE;
    public static final String RANGE_MESSAGE = "must not be after to";

    public CallLogQuery {
        correlationId = trimToNull(correlationId);
    }

    public static CallLogQuery of(String correlationId, Instant from, Instant to, Integer page, Integer size) {
        CallLogQuery query = new CallLogQuery(correlationId, from, to, getIfNull(page, 0), getIfNull(size, DEFAULT_SIZE));
        ValidationProblems problems = new ValidationProblems();
        if (query.page() < 0) {
            problems.add("page", PAGE_MESSAGE);
        }
        if (query.size() < 1 || query.size() > MAX_SIZE) {
            problems.add("size", SIZE_MESSAGE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            problems.add("from", RANGE_MESSAGE);
        }
        problems.throwIfAny();
        return query;
    }
}
