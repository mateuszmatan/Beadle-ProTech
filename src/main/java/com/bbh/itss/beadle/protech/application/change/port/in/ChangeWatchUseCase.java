package com.bbh.itss.beadle.protech.application.change.port.in;

import com.bbh.itss.beadle.protech.domain.change.WatchReport;

public interface ChangeWatchUseCase {

    /** Compares the changes Beadle-GUI knows with ProTech and tells Beadle-GUI of each one that moved. */
    WatchReport tick();
}
