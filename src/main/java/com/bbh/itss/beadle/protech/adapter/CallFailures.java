package com.bbh.itss.beadle.protech.adapter;

import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;

import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.truncate;

/** How a call to ProTech or Beadle-GUI that failed is told: the system, what it could not do and why, in short. */
@NoArgsConstructor(access = PRIVATE)
public final class CallFailures {

    /** The longest reason of another system that a failure repeats. */
    public static final int MAX_REASON = 300;

    /** {@code <system> could not <operation>: <the message of the cause>}. */
    public static UncheckedIOException couldNot(String system, String operation, Exception cause) {
        return couldNot(system, operation, defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName()), cause);
    }

    /** {@code <system> could not <operation>: <reason>}, the reason cut to {@link #MAX_REASON} characters. */
    public static UncheckedIOException couldNot(String system, String operation, String reason, Exception cause) {
        return new UncheckedIOException(system + " could not " + operation + ": " + truncate(reason, MAX_REASON),
                new IOException(cause));
    }
}
