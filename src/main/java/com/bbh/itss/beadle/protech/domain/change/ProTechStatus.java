package com.bbh.itss.beadle.protech.domain.change;

/** Which ProTech the service talks to; only the live one is connected. */
public record ProTechStatus(ProTechMode mode) {

    public boolean connected() {
        return mode == ProTechMode.LIVE;
    }
}
