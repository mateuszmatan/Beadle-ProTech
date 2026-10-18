package com.bbh.itss.beadle.protech.domain.change;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/** Beadle-GUI's note of its last update of a change, handed back as it came. */
public record ChangeUpdate(Status status, Instant requestedAt, String departmentName, List<String> fields,
                           String message, Instant checkedAt) {

    public enum Status { PENDING, APPLIED, NOT_APPLIED }

    public ChangeUpdate {
        fields = List.copyOf(emptyIfNull(fields));
    }
}
