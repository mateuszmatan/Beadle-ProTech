package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SECONDARY_APPROVAL;
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SUPPORT_APPROVAL;

@RequiredArgsConstructor
public enum ApprovalRole {
    BUSINESS("business approver", BUSINESS_APPROVAL, Approvers::businessApprover),
    L1("L1 approver", PRIMARY_APPROVAL, Approvers::l1Manager),
    L2("L2 approver", SECONDARY_APPROVAL, Approvers::l2Manager),
    SUPPORT("support approver", SUPPORT_APPROVAL, Approvers::supportApprover);

    @Getter
    private final String label;
    @Getter
    private final ChangeState stage;
    private final Function<Approvers, String> named;

    public String approverIn(Approvers approvers) {
        return named.apply(approvers);
    }
}
