package com.hrgenius.performance.dto;

import com.hrgenius.performance.entity.PerformanceEnums.FeedbackKind;
import com.hrgenius.performance.entity.PerformanceEnums.GoalStatus;
import com.hrgenius.performance.entity.PerformanceEnums.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request/response shapes for performance management. */
public final class PerformanceDtos {

    private PerformanceDtos() {
    }

    // ------------------------------------------------------------ cycles

    public record CycleDto(Long id, String name, LocalDate startDate, LocalDate endDate, LocalDate selfReviewDue,
                           LocalDate managerReviewDue, String status, Instant launchedAt, Instant closedAt,
                           int reviewCount, int completedCount) {
    }

    public record CycleRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalDate selfReviewDue,
            LocalDate managerReviewDue) {
    }

    public record CycleSummaryDto(CycleDto cycle, Map<String, Long> byStatus, Map<Integer, Long> ratingDistribution,
                                  BigDecimal averageScore) {
    }

    // ------------------------------------------------------------ reviews

    public record ReviewSummaryDto(Long id, Long cycleId, String cycleName, String cycleStatus, Long employeeId,
                                   String employeeCode, String employeeName, String designation, Long reviewerId,
                                   String reviewerName, String status, int goalCount, Integer managerRating,
                                   BigDecimal finalScore, Instant selfSubmittedAt, Instant managerSubmittedAt) {
    }

    public record GoalDto(Long id, String title, String description, int weight, LocalDate targetDate, int progress,
                          String status, Integer selfRating, String selfComment, Integer managerRating,
                          String managerComment) {
    }

    /**
     * A review as the viewer may see it: the manager's assessment is hidden from the employee until
     * submitted, and the employee's self-assessment is hidden from the manager until submitted.
     */
    public record ReviewDto(Long id, Long cycleId, String cycleName, String cycleStatus, LocalDate selfReviewDue,
                            LocalDate managerReviewDue, Long employeeId, String employeeCode, String employeeName,
                            String designation, Long reviewerId, String reviewerName, String status,
                            Integer selfRating, String selfComments, Integer managerRating, String managerComments,
                            BigDecimal finalScore, String ackComment, Instant selfSubmittedAt,
                            Instant managerSubmittedAt, Instant acknowledgedAt, List<GoalDto> goals,
                            int totalWeight, String viewerRole, boolean canEditGoals, boolean canUpdateProgress,
                            boolean canSelfReview, boolean canManagerReview, boolean canAcknowledge) {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @NotNull @Min(0) @Max(100) Integer weight,
            LocalDate targetDate) {
    }

    public record ProgressRequest(
            @NotNull @Min(0) @Max(100) Integer progress,
            @NotNull GoalStatus status) {
    }

    public record GoalRatingInput(@NotNull Long goalId, @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    }

    /** Used for both the self and the manager assessment; {@code submit=false} saves a draft. */
    public record AssessmentRequest(
            @Min(1) @Max(5) Integer overallRating,
            @Size(max = 4000) String comments,
            @NotNull List<@Valid GoalRatingInput> goals,
            Boolean submit) {
    }

    public record AcknowledgeRequest(@Size(max = 1000) String comment) {
    }

    // ------------------------------------------------------------ feedback

    public record FeedbackDto(Long id, Long fromId, String fromName, Long toId, String toName, String kind,
                              String visibility, String message, Instant createdAt) {
    }

    public record FeedbackRequest(
            @NotNull Long toEmployeeId,
            @NotNull FeedbackKind kind,
            @NotNull Visibility visibility,
            @NotBlank @Size(max = 1000) String message) {
    }
}
