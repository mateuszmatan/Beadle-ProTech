package com.bbh.itss.beadle.protech.domain.change;

import static com.bbh.itss.beadle.protech.domain.change.ApprovalState.NOT_APPROVED;
import static com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.beadle.protech.domain.shared.Text.abbreviateBytes;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ChangeApproval(ApprovalRole role, String approver, ApprovalState state, Reminder reminder) {

    public ChangeApproval {
        approver = abbreviateBytes(trimToNull(approver), GROUP_MAX);
        state = getIfNull(state, NOT_APPROVED);
        reminder = reminder == null || reminder.sentAt() == null ? null : reminder;
    }
}
