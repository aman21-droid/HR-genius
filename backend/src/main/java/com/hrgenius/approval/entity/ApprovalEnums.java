package com.hrgenius.approval.entity;

/**
 * Enumerations for the generic approval engine. {@code subjectType} is deliberately a free
 * string (LEAVE, REGULARIZATION, ...) so new modules can plug in without changing an enum.
 */
public final class ApprovalEnums {

    private ApprovalEnums() {
    }

    /** Overall state of an approval flow. */
    public enum ApprovalStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    /** State of a single step. SKIPPED is used when a later decision short-circuits the flow. */
    public enum StepStatus { PENDING, APPROVED, REJECTED, SKIPPED }
}
