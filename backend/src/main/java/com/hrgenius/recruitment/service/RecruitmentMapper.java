package com.hrgenius.recruitment.service;

import com.hrgenius.employee.entity.Employee;
import com.hrgenius.org.entity.MasterEntity;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.*;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Entity -> DTO conversions shared by the recruitment services. Must be called inside a
 * transaction because it walks lazy associations.
 */
final class RecruitmentMapper {

    private RecruitmentMapper() {
    }

    static RequisitionDto requisition(JobRequisition r, long activeApplications) {
        return new RequisitionDto(r.getId(), r.getReqCode(), r.getTitle(),
                idOf(r.getDepartment()), nameOf(r.getDepartment()),
                idOf(r.getDesignation()), nameOf(r.getDesignation()),
                idOf(r.getLocation()), nameOf(r.getLocation()),
                idOf(r.getGrade()), nameOf(r.getGrade()),
                r.getHiringManager().getId(), r.getHiringManager().getFullName(),
                r.getEmploymentType().name(), r.getOpenings(), r.getFilled(),
                r.getMinExperience(), r.getMaxExperience(), r.getSalaryMin(), r.getSalaryMax(),
                r.getSkills(), r.getDescription(), r.getTargetDate(), r.isPublishOnCareers(),
                r.getStatus().name(), r.getApprovalRequestId(), r.getOpenedAt(), r.getClosedAt(),
                activeApplications, r.getCreatedAt());
    }

    static CandidateSummaryDto candidateSummary(Candidate c, int applications) {
        return new CandidateSummaryDto(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(),
                c.getCurrentTitle(), c.getCurrentCompany(), c.getTotalExperience(), c.getCity(),
                c.getSource().name(), c.getResumeKey() != null, applications, c.getCreatedAt());
    }

    static CandidateDto candidate(Candidate c, List<CandidateApplicationDto> applications) {
        Employee ref = c.getReferredBy();
        return new CandidateDto(c.getId(), c.getFirstName(), c.getLastName(), c.getFullName(), c.getEmail(),
                c.getPhone(), c.getCurrentCompany(), c.getCurrentTitle(), c.getTotalExperience(),
                c.getCurrentCtc(), c.getExpectedCtc(), c.getNoticePeriodDays(), c.getCity(), c.getLinkedinUrl(),
                c.getSource().name(), ref != null ? ref.getId() : null, ref != null ? ref.getFullName() : null,
                c.getResumeKey() != null, c.getResumeFileName(), c.getResumeSize(), c.getNotes(),
                applications, c.getCreatedAt());
    }

    static CandidateApplicationDto candidateApplication(JobApplication a) {
        JobRequisition r = a.getRequisition();
        return new CandidateApplicationDto(a.getId(), r.getId(), r.getReqCode(), r.getTitle(),
                a.getStage().name(), a.getStageChangedAt());
    }

    static ApplicationCardDto card(JobApplication a, List<Interview> interviews) {
        Candidate c = a.getCandidate();
        OptionalDouble avg = interviews.stream()
                .flatMap(i -> i.getFeedback().stream())
                .filter(f -> f.getRating() != null)
                .mapToInt(InterviewFeedback::getRating)
                .average();
        int pending = (int) interviews.stream()
                .filter(i -> i.getStatus() == RecruitmentEnums.InterviewStatus.SCHEDULED)
                .flatMap(i -> i.getFeedback().stream())
                .filter(f -> !f.isSubmitted())
                .count();
        return new ApplicationCardDto(a.getId(), c.getId(), c.getFullName(), c.getEmail(), c.getCurrentTitle(),
                c.getCurrentCompany(), c.getTotalExperience(), a.getStage().name(), a.getStageChangedAt(),
                a.getSource().name(), avg.isPresent() ? Math.round(avg.getAsDouble() * 10) / 10.0 : null,
                interviews.size(), pending, c.getResumeKey() != null);
    }

    static ApplicationEventDto event(ApplicationEvent e) {
        return new ApplicationEventDto(e.getId(), e.getEventType().name(),
                e.getFromStage() != null ? e.getFromStage().name() : null,
                e.getToStage() != null ? e.getToStage().name() : null,
                e.getMessage(), e.getActorName(), e.getCreatedAt());
    }

    static InterviewDto interview(Interview i) {
        return new InterviewDto(i.getId(), i.getApplication().getId(), i.getRoundName(), i.getMode().name(),
                i.getScheduledAt(), i.getDurationMinutes(), i.getLocationOrLink(), i.getStatus().name(),
                i.getFeedback().stream().map(RecruitmentMapper::feedback).toList());
    }

    static FeedbackDto feedback(InterviewFeedback f) {
        return new FeedbackDto(f.getId(), f.getInterviewer().getId(), f.getInterviewer().getFullName(),
                f.getRating(), f.getRecommendation() != null ? f.getRecommendation().name() : null,
                f.getStrengths(), f.getConcerns(), f.getSubmittedAt());
    }

    static MyInterviewDto myInterview(InterviewFeedback f) {
        Interview i = f.getInterview();
        JobApplication a = i.getApplication();
        Candidate c = a.getCandidate();
        JobRequisition r = a.getRequisition();
        return new MyInterviewDto(i.getId(), f.getId(), i.getRoundName(), i.getMode().name(), i.getScheduledAt(),
                i.getDurationMinutes(), i.getLocationOrLink(), i.getStatus().name(),
                c.getId(), c.getFullName(), c.getCurrentTitle(), c.getCurrentCompany(), c.getTotalExperience(),
                c.getResumeKey() != null, r.getReqCode(), r.getTitle(),
                f.isSubmitted(), f.getRating(), f.getRecommendation() != null ? f.getRecommendation().name() : null,
                f.getStrengths(), f.getConcerns(), f.getSubmittedAt());
    }

    static OfferDto offer(Offer o) {
        JobApplication a = o.getApplication();
        return new OfferDto(o.getId(), a.getId(), a.getCandidate().getFullName(), a.getRequisition().getTitle(),
                idOf(o.getDesignation()), nameOf(o.getDesignation()),
                idOf(o.getDepartment()), nameOf(o.getDepartment()),
                idOf(o.getLocation()), nameOf(o.getLocation()),
                idOf(o.getGrade()), nameOf(o.getGrade()),
                o.getEmploymentType().name(), o.getAnnualCtc(), o.getJoiningDate(), o.getExpiryDate(), o.getNotes(),
                o.getStatus().name(), o.getApprovalRequestId(), o.getSentAt(), o.getRespondedAt(),
                o.getDeclineReason(), o.getCreatedAt());
    }

    private static Long idOf(MasterEntity m) {
        return m == null ? null : m.getId();
    }

    private static String nameOf(MasterEntity m) {
        return m == null ? null : m.getName();
    }
}
