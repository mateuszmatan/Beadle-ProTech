package com.bbh.itss.beadle.protech.domain.change;

import java.time.Instant;

/**
 * The callback that tells Beadle-GUI that ProTech moved a change: {@code POST /api/callbacks/protech} with
 * {@code {"number", "event", "state", "at", "correlationId"}}; Beadle-GUI then reads the change again.
 */
public record ChangeCallback(String number, ChangeEvent event, ChangeState state, Instant at, String correlationId) {
}
