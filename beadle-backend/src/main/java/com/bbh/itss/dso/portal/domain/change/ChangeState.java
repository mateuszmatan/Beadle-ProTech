package com.bbh.itss.dso.portal.domain.change;

public enum ChangeState {
    DRAFT, BUSINESS_APPROVAL, PRIMARY_APPROVAL, SECONDARY_APPROVAL, CTASK_APPROVAL, ESCALATED_APPROVAL, IMPLEMENTATION,
    CLOSED;

    public boolean isOpen() {
        return this != CLOSED;
    }
}
