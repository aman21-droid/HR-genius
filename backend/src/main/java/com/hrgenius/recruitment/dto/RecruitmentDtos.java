package com.hrgenius.recruitment.dto;

import com.hrgenius.employee.entity.EmployeeEnums.EmploymentType;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import com.hrgenius.recruitment.entity.RecruitmentEnums.InterviewMode;
import com.hrgenius.recruitment.entity.RecruitmentEnums.InterviewStatus;
import com.hrgenius.recruitment.entity.RecruitmentEnums.Recommendation;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for the recruitment (ATS) API. */
public final class RecruitmentDtos {

    private RecruitmentDtos() {
    }

    // ------------------------------------------------------------ requisitions

    public record RequisitionDto(
            Long id, String reqCode, String title,
            Long departmentId, String departmentName, Long designationId, String designationName,
            Long locationId, String locationName, Long gradeId, String gradeName,
            Long hiringManagerId, String hiringManagerName, String employmentType,
            int openings, int filled, BigDecimal minExperience, BigDecimal maxExperience,
            BigDecimal salaryMin, BigDecimal salaryMax, String skills, String description,
            LocalDate targetDate, boolean publishOnCareers, String status, Long approvalRequestId,
            Instant openedAt, Instant closedAt, long activeApplications, Instant createdAt) {
    }

    public record RequisitionRequest(
            @NotBlank @Size(max = 160) String title,
            @NotNull Long departmentId,
            @NotNull Long designationId,
            @NotNull Long locationId,
            Long gradeId,
            @NotNull Long hiringManagerId,
            @NotNull EmploymentType employmentType,
            @NotNull @Min(1) @Max(500) Integer openings,
            @DecimalMin("0.0") @DecimalMax("50.0") BigDecimal minExperience,
            @DecimalMin("0.0") @DecimalMax("50.0") BigDecimal maxExperience,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal salaryMin,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal salaryMax,
            @Size(max = 500) String skills,
            @Size(max = 4000) String description,
            LocalDate targetDate,
            Boolean publishOnCareers) {
    }

    public record RequisitionFilter(String search, RequisitionStatus status, Long departmentId) {
    }

    /** Hold / reopen / close / cancel. */
    public record RequisitionStatusRequest(@NotNull RequisitionStatus status) {
    }

    // ------------------------------------------------------------ candidates

    public record CandidateSummaryDto(
            Long id, String fullName, String email, String phone, String currentTitle, String currentCompany,
            BigDecimal totalExperience, String city, String source, boolean hasResume, int applications,
            Instant createdAt) {
    }

    public record CandidateDto(
            Long id, String firstName, String lastName, String fullName, String email, String phone,
            String currentCompany, String currentTitle, BigDecimal totalExperience,
            BigDecimal currentCtc, BigDecimal expectedCtc, Integer noticePeriodDays, String city,
            String linkedinUrl, String source, Long referredById, String referredByName,
            boolean hasResume, String resumeFileName, Long resumeSize, String notes,
            List<CandidateApplicationDto> applications, Instant createdAt) {
    }

    /** One of a candidate's applications, seen from the candidate profile. */
    public record CandidateApplicationDto(
            Long applicationId, Long requisitionId, String reqCode, String requisitionTitle,
            String stage, Instant stageChangedAt) {
    }

    public record CandidateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = "^[+0-9 ()-]{7,30}$", message = "must be a valid phone number") String phone,
            @Size(max = 120) String currentCompany,
            @Size(max = 120) String currentTitle,
            @DecimalMin("0.0") @DecimalMax("60.0") BigDecimal totalExperience,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal currentCtc,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal expectedCtc,
            @Min(0) @Max(365) Integer noticePeriodDays,
            @Size(max = 80) String city,
            @Size(max = 300) @Pattern(regexp = "^(https?://.*)?$", message = "must be an http(s) URL") String linkedinUrl,
            CandidateSource source,
            Long referredById,
            @Size(max = 2000) String notes) {
    }

    public record CandidateFilter(String search, CandidateSource source) {
    }

    // ------------------------------------------------------------ applications

    /** A card on the pipeline board. */
    public record ApplicationCardDto(
            Long id, Long candidateId, String candidateName, String email, String currentTitle,
            String currentCompany, BigDecimal totalExperience, String stage, Instant stageChangedAt,
            String source, Double averageRating, int interviews, int feedbackPending, boolean hasResume) {
    }

    public record PipelineDto(RequisitionDto requisition, List<ApplicationCardDto> applications) {
    }

    public record ApplicationDetailDto(
            Long id, RequisitionDto requisition, CandidateDto candidate, String stage, Instant stageChangedAt,
            String source, String coverNote, String rejectionReason, Long employeeId,
            List<ApplicationEventDto> events, List<InterviewDto> interviews, List<OfferDto> offers,
            Instant createdAt) {
    }

    public record ApplicationEventDto(
            Long id, String eventType, String fromStage, String toStage, String message, String actorName,
            Instant createdAt) {
    }

    public record CreateApplicationRequest(
            @NotNull Long requisitionId,
            @NotNull Long candidateId,
            CandidateSource source,
            @Size(max = 2000) String coverNote) {
    }

    public record MoveStageRequest(
            @NotNull ApplicationStage stage,
            @Size(max = 500) String note) {
    }

    public record NoteRequest(@NotBlank @Size(max = 1000) String message) {
    }

    // ------------------------------------------------------------ interviews

    public record InterviewDto(
            Long id, Long applicationId, String roundName, String mode, Instant scheduledAt, int durationMinutes,
            String locationOrLink, String status, List<FeedbackDto> panel) {
    }

    public record FeedbackDto(
            Long id, Long interviewerId, String interviewerName, Integer rating, String recommendation,
            String strengths, String concerns, Instant submittedAt) {
    }

    public record ScheduleInterviewRequest(
            @NotBlank @Size(max = 80) String roundName,
            @NotNull InterviewMode mode,
            @NotNull @Future Instant scheduledAt,
            @NotNull @Min(15) @Max(480) Integer durationMinutes,
            @Size(max = 300) String locationOrLink,
            @NotEmpty @Size(max = 10) List<@NotNull Long> panelistIds) {
    }

    public record InterviewUpdateRequest(
            InterviewStatus status,
            Instant scheduledAt,
            @Size(max = 300) String locationOrLink) {
    }

    public record SubmitFeedbackRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @NotNull Recommendation recommendation,
            @Size(max = 2000) String strengths,
            @Size(max = 2000) String concerns) {
    }

    /** A panelist's view of an interview they sit on, with their own scorecard. */
    public record MyInterviewDto(
            Long interviewId, Long feedbackId, String roundName, String mode, Instant scheduledAt,
            int durationMinutes, String locationOrLink, String interviewStatus,
            Long candidateId, String candidateName, String currentTitle, String currentCompany,
            BigDecimal totalExperience, boolean hasResume, String reqCode, String requisitionTitle,
            boolean submitted, Integer rating, String recommendation, String strengths, String concerns,
            Instant submittedAt) {
    }

    // ------------------------------------------------------------ offers

    public record OfferDto(
            Long id, Long applicationId, String candidateName, String requisitionTitle,
            Long designationId, String designationName, Long departmentId, String departmentName,
            Long locationId, String locationName, Long gradeId, String gradeName, String employmentType,
            BigDecimal annualCtc, LocalDate joiningDate, LocalDate expiryDate, String notes, String status,
            Long approvalRequestId, Instant sentAt, Instant respondedAt, String declineReason, Instant createdAt) {
    }

    public record OfferRequest(
            @NotNull Long designationId,
            @NotNull Long departmentId,
            @NotNull Long locationId,
            Long gradeId,
            @NotNull EmploymentType employmentType,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal annualCtc,
            @NotNull LocalDate joiningDate,
            LocalDate expiryDate,
            @Size(max = 1000) String notes) {
    }

    /** Records the candidate's answer to a SENT offer. */
    public record OfferResponseRequest(
            @NotNull Boolean accepted,
            @Size(max = 500) String declineReason) {
    }

    /** Converts an accepted offer into an employee record (and optionally a login and onboarding plan). */
    public record HireRequest(
            @NotBlank @Email @Size(max = 160) String workEmail,
            Long managerId,
            LocalDate dateOfJoining,
            Boolean createLogin,
            Boolean startOnboarding,
            Long onboardingTemplateId) {
    }

    public record HireResponse(
            Long employeeId, String employeeCode, String employeeName, String loginEmail,
            String temporaryPassword, Long onboardingPlanId) {
    }

    // ------------------------------------------------------------ public careers

    public record CareerJobDto(
            String reqCode, String title, String department, String location, String employmentType,
            BigDecimal minExperience, BigDecimal maxExperience, String skills, String description,
            Instant postedAt) {
    }

    public record CareerApplyForm(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = "^[+0-9 ()-]{7,30}$", message = "must be a valid phone number") String phone,
            @Size(max = 120) String currentCompany,
            @Size(max = 120) String currentTitle,
            @DecimalMin("0.0") @DecimalMax("60.0") BigDecimal totalExperience,
            @Min(0) @Max(365) Integer noticePeriodDays,
            @Size(max = 80) String city,
            @Size(max = 300) @Pattern(regexp = "^(https?://.*)?$", message = "must be an http(s) URL") String linkedinUrl,
            @Size(max = 2000) String coverNote,
            /* honeypot: real users never see or fill this field */
            String website) {
    }
}
