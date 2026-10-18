package com.bbh.itss.beadle.protech.domain.change;

import com.bbh.itss.beadle.protech.domain.change.ChangeTemplate.Approvers;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

import static com.bbh.itss.beadle.protech.domain.change.ChangeState.BUSINESS_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.PRIMARY_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.SECONDARY_APPROVAL;
import static com.bbh.itss.beadle.protech.domain.change.ChangeState.SUPPORT_APPROVAL;

@RequiredArgsConstructor
public enum ApprovalRole {
    BUSINESS("business approver", BUSINESS_APPROVAL, Approvers::businessApprover),
    L1("L1 approver", PRIMARY_APPROVAL, Approvers::l1Manager),
    L2("L2 approver", SECONDARY_APPROVAL, Approvers::l2Manager),
    SUPPORT("support approver", SUPPORT_APPROVAL, Approvers::supportApprover);

    @Getter
    private final String label;
    private final ChangeState stage;
    private final Function<Approvers, String> named;

    public String approverIn(Approvers approvers) {
        return named.apply(approvers);
    }

    /** The approval of the role in a change in {@code state}: requested in its stage and given past it. */
    public ApprovalState approvalIn(ChangeState state) {
        int reached = state.compareTo(stage);
        return reached < 0 ? ApprovalState.NOT_APPROVED : reached == 0 ? ApprovalState.REQUESTED
                : ApprovalState.APPROVED;
    }
}
