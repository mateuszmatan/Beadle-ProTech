package com.bbh.itss.dso.portal.domain.change;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApprovalState {
    NOT_APPROVED("Not Approved"), REQUESTED("Requested"), APPROVED("Approved");

    private final String label;

    public boolean approved() {
        return this == APPROVED;
    }
}
