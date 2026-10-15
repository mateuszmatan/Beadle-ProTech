package com.bbh.itss.beadle.protech.application.change.port.out;

import com.bbh.itss.beadle.protech.domain.change.TrackedChange;

import java.util.Collection;
import java.util.Optional;

/** The changes Beadle raised or read, which Beadle-ProTech watches for Beadle-GUI. */
public interface TrackedChangePort {

    /**
     * Follows the changes as Beadle-GUI knows them now, each merged over what was known of it before. The changes
     * are in ProTech whatever happens here, so a change that cannot be written down is reported in the log and never
     * fails the call that raised or read it.
     */
    void track(Collection<TrackedChange> changes);

    Optional<TrackedChange> find(String number);
}
