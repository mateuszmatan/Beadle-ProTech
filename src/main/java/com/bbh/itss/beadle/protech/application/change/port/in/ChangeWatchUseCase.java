package com.bbh.itss.beadle.protech.application.change.port.in;

public interface ChangeWatchUseCase {

    /** Compares the changes Beadle-GUI knows with ProTech and tells Beadle-GUI of each one that moved. */
    void tick();

    /**
     * Looks at once at the change numbered {@code number}, which ProTech says it moved through its webhook, whatever
     * the poll interval, and tells Beadle-GUI if it moved past what it knows. A change not followed is left alone.
     */
    void look(String number);
}
