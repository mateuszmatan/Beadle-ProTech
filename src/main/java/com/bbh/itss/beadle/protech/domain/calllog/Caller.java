package com.bbh.itss.beadle.protech.domain.calllog;

/** The user signed in to Beadle-GUI who caused a call, and the correlation id of that user action. */
public record Caller(String user, String correlationId) {

    public static final Caller NONE = new Caller(null, null);
    /** The user of the work Beadle-ProTech does on its own, such as the poll and the callbacks. */
    public static final String SYSTEM = "system";
    /** The user of a call that names none, such as the webhook ProTech calls. */
    public static final String UNKNOWN = "unknown";

    public static Caller system(String correlationId) {
        return new Caller(SYSTEM, correlationId);
    }
}
