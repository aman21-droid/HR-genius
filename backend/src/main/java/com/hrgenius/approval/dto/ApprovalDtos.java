package com.hrgenius.approval.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** API payloads for the approval inbox and decisions. */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /** One actionable item in an approver's inbox (the active step of an open request). */
    public record InboxItem(
            Long stepId, Long requestId, String subjectType, Long subjectId, String title,
            Long requesterEmpId, String requesterName, String roleHint,
            int stepNo, int totalSteps, Instant createdAt) {
    }

    public record StepView(
            int stepNo, Long approverEmpId, String approverName, String roleHint,
            String status, String comment, Instant decidedAt) {
    }

    /** A request the current user raised, with its full step trail. */
    public record MyRequestItem(
            Long requestId, String subjectType, Long subjectId, String title, String status,
            int currentStep, int totalSteps, List<StepView> steps, Instant createdAt, Instant resolvedAt) {
    }

    public record DecisionRequest(
            @NotNull Boolean approve,
            @Size(max = 500) String comment) {
    }
}
