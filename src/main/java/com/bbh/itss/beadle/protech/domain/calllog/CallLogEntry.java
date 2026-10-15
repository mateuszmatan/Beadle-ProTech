package com.bbh.itss.beadle.protech.domain.calllog;

import java.time.Instant;

/**
 * One call of Beadle-ProTech to another system: who caused it in Beadle-GUI, the correlation id of that user action,
 * the system and operation, what it was about, how it ended and how long it took.
 */
public record CallLogEntry(Long id, Instant at, String userName, String correlationId, ExternalSystem system,
                           String operation, String target, Outcome outcome, String message, long durationMs) {

    public static final int USER_MAX = 200;
    public static final int CORRELATION_MAX = 100;
    public static final int TARGET_MAX = 500;
    public static final int MESSAGE_MAX = 2000;
}
