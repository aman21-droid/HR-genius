package com.hrgenius.approval.service;

import com.hrgenius.approval.entity.ApprovalRequest;

/**
 * A module plugs into the approval engine by implementing this for its {@code subjectType}.
 * The engine calls back exactly once when a flow resolves, so the domain can finalize
 * (e.g. Leave converts reserved balance to used on approval, or releases it on rejection).
 */
public interface ApprovalOutcomeHandler {

    /** The subject type this handler owns, e.g. "LEAVE" or "REGULARIZATION". */
    String subjectType();

    /** Called when every step has approved. */
    void onApproved(ApprovalRequest request);

    /** Called when any step rejects (or the flow is cancelled by the requester). */
    void onRejected(ApprovalRequest request, String reason);
}
