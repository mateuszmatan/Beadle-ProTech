package com.bbh.itss.beadle.protech.domain.calllog;

/** The user signed in to Beadle-GUI who caused a call, and the correlation id of that user action. */
public record Caller(String user, String correlationId) {

    public static final Caller NONE = new Caller(null, null);
}
