package com.bbh.itss.beadle.protech.domain.change;

/** Whom a reminder goes to: the approver of a role of the change, or the approvers of one of its tasks. */
public record ApprovalRef(ApprovalRole role, String task) {
}
