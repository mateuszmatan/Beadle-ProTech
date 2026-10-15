package com.bbh.itss.beadle.protech.application.change.port.out;

import com.bbh.itss.beadle.protech.domain.change.TrackedChange;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The changes Beadle raised or read, which Beadle-ProTech watches for Beadle-GUI. */
public interface TrackedChangePort {

    /**
     * Follows the changes as Beadle-GUI knows them now, each merged over what was known of it before. The changes
     * are in ProTech whatever happens here, so a change that cannot be written down is reported in the log and never
     * fails the call that raised or read it.
     */
    void track(Collection<TrackedChange> changes);

    /**
     * Notes that Beadle-GUI acted on a change it knows, which it now knows up to {@code at}, under the correlation id
     * of that action. A change not followed stays so, and a failure is reported in the log and never fails the call.
     */
    void touch(String number, Instant at, String correlationId);

    Optional<TrackedChange> find(String number);

    /** The changes followed and not closed, by number. */
    List<TrackedChange> watched();

    void save(TrackedChange change);

    /** Notes that the changes were looked at {@code at} and nothing new was found. */
    void seen(Collection<String> numbers, Instant at);
}
