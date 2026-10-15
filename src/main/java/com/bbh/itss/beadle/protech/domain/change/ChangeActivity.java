package com.bbh.itss.beadle.protech.domain.change;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What ProTech did to a change lately: its state and since when it holds it ({@code null} when ProTech did not
 * touch the change itself since the time asked), and the last time one of its approvals or tasks moved
 * ({@code null} when none did).
 */
public record ChangeActivity(String number, ChangeState state, Instant stateAt, Instant approvalsAt,
                             Instant tasksAt) {

    /** The last time ProTech moved anything of the change, or {@code null} when it moved nothing. */
    public Instant latest() {
        return Stream.of(stateAt, approvalsAt, tasksAt).filter(Objects::nonNull).max(Instant::compareTo)
                .orElse(null);
    }
}
