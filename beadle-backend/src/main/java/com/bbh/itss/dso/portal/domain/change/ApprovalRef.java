package com.bbh.itss.dso.portal.domain.change;

public record ApprovalRef(ApprovalRole role, String task) {

    public static ApprovalRef of(ApprovalRole role) {
        return new ApprovalRef(role, null);
    }

    public static ApprovalRef ofTask(String number) {
        return new ApprovalRef(null, number);
    }
}
