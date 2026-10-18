package com.bbh.itss.beadle.protech.domain.change;

public enum ApprovalState {
    NOT_APPROVED, REQUESTED, APPROVED;

    public boolean approved() {
        return this == APPROVED;
    }
}
