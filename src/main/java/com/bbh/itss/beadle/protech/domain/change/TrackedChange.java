package com.bbh.itss.beadle.protech.domain.change;

import java.time.Instant;
import java.util.Optional;

import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.APPROVAL_CHANGED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.STATE_CHANGED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeEvent.TASK_CHANGED;
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

    /**
     * A change Beadle-GUI just acted on (a task created, an update, a reminder): what ProTech did to it until
     * {@code at} is known to Beadle-GUI, which leaves the state as it was.
     */
    public static TrackedChange touched(String number, Instant at, String correlationId) {
        return new TrackedChange(number, null, at, at, correlationId);
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

    /**
     * The callback that tells Beadle-GUI what ProTech did that it does not know yet, if anything: a new state first,
     * otherwise an approval, otherwise a task that moved after the last movement Beadle-GUI knows of. One callback
     * covers the change: Beadle-GUI reads all of it again.
     */
    public Optional<ChangeCallback> callbackFor(ChangeActivity activity, Instant now) {
        ChangeState current = getIfNull(activity.state(), state);
        if (activity.state() != null && activity.state() != state) {
            return Optional.of(new ChangeCallback(number, STATE_CHANGED, current, getIfNull(activity.stateAt(), now),
                    correlationId));
        }
        if (unknown(activity.approvalsAt())) {
            return Optional.of(new ChangeCallback(number, APPROVAL_CHANGED, current, activity.approvalsAt(),
                    correlationId));
        }
        if (unknown(activity.tasksAt())) {
            return Optional.of(new ChangeCallback(number, TASK_CHANGED, current, activity.tasksAt(), correlationId));
        }
        return Optional.empty();
    }

    /** This change once Beadle-GUI took the callback about {@code activity}: everything in it is known. */
    public TrackedChange reported(ChangeActivity activity, Instant now) {
        return new TrackedChange(number, getIfNull(activity.state(), state), max(lastSeenAt, now),
                max(lastEventAt, activity.latest()), correlationId);
    }

    private boolean unknown(Instant movedAt) {
        return movedAt != null && (lastEventAt == null || movedAt.isAfter(lastEventAt));
    }
}
