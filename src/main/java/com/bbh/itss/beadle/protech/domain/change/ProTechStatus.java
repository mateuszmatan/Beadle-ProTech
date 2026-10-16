package com.bbh.itss.beadle.protech.domain.change;

/** Whether ProTech is reached and which ProTech it is. */
public record ProTechStatus(boolean connected, ProTechMode mode) {
}
