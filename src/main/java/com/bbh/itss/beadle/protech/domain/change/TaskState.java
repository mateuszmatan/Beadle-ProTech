package com.bbh.itss.beadle.protech.domain.change;

public enum TaskState {
    OPEN, WORK_IN_PROGRESS, CLOSED, CANCELED;

    public boolean frozen() {
        return this == CLOSED || this == CANCELED;
    }
}
