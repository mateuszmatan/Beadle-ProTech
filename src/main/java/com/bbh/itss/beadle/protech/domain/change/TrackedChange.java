package com.bbh.itss.beadle.protech.domain.change;

import java.time.Instant;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.ObjectUtils.max;

/**
 * A change Beadle raised or read, followed so that Beadle-GUI hears when ProTech moves it. {@code state} is the state
 * Beadle-GUI knows of (the one it raised or last read, or the one of the last callback, {@code null} while ProTech
 * answers a state Beadle does not know), {@code lastEventAt} the time of the last movement Beadle-GUI knows of,
 * {@code lastSeenAt} the last time Beadle-ProTech looked at the change and {@code correlationId} the user action of
 * Beadle-GUI the callbacks of the change go back to.
 */
public record TrackedChange(String number, ChangeState state, Instant lastSeenAt, Instant lastEventAt,
                            String correlationId) {

    public static final int NUMBER_MAX = 40;

    /** A change as Beadle-GUI came to know it at {@code at}: everything ProTech did to it until then is known. */
    public static TrackedChange seen(String number, ChangeState state, Instant at, String correlationId) {
        return new TrackedChange(number, state, at, at, correlationId);
    }

    /** A change is followed until it is closed. */
    public boolean watched() {
        return state == null || state.isOpen();
    }

    /**
     * This change as {@code newer} tells it: the newer state and correlation id, and the later of the times, so that a
     * movement already reported is not reported again.
     */
    public TrackedChange updatedBy(TrackedChange newer) {
        return new TrackedChange(number, getIfNull(newer.state(), state), max(lastSeenAt, newer.lastSeenAt()),
                max(lastEventAt, newer.lastEventAt()), getIfNull(newer.correlationId(), correlationId));
    }
}
