package com.bbh.itss.beadle.protech.adapter;

import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PRIVATE;

/** The headers every call between the Beadle services carries beside the service token. */
@NoArgsConstructor(access = PRIVATE)
public final class BeadleHeaders {

    /** The user signed in to Beadle-GUI, percent-encoded in UTF-8; {@code system} for the work of a service. */
    public static final String USER = "X-Beadle-User";
    /** The correlation id of the user action, at most {@code CallLogEntry.CORRELATION_MAX} characters. */
    public static final String CORRELATION_ID = "X-Correlation-Id";
}
