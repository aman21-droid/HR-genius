package com.hrgenius.recruitment.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.Interview;
import com.hrgenius.recruitment.entity.InterviewFeedback;
import com.hrgenius.recruitment.entity.JobApplication;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationEventType;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.InterviewStatus;
import com.hrgenius.recruitment.repository.InterviewFeedbackRepository;
import com.hrgenius.recruitment.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Interview rounds and panel scorecards. Scheduling creates one feedback row per panelist;
 * a round completes automatically once every panelist has submitted.
 */
@Service
public class InterviewService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault());

    private final InterviewRepository interviews;
    private final InterviewFeedbackRepository feedback;
    private final EmployeeRepository employees;
    private final ApplicationService applicationService;

    public InterviewService(InterviewRepository interviews, InterviewFeedbackRepository feedback,
                            EmployeeRepository employees, ApplicationService applicationService) {
        this.interviews = interviews;
        this.feedback = feedback;
        this.employees = employees;
        this.applicationService = applicationService;
    }

    /** Schedules a round; an application still at APPLIED/SCREENING moves to INTERVIEW. */
    @Transactional
    public InterviewDto schedule(Long applicationId, ScheduleInterviewRequest req) {
        JobApplication a = applicationService.find(applicationId);
        if (a.getStage().isTerminal()) {
            throw new BusinessException("Cannot schedule an interview for a " + a.getStage().name().toLowerCase()
                    + " application");
        }
        Set<Long> panelIds = new LinkedHashSet<>(req.panelistIds());
        Interview i = new Interview();
        i.setApplication(a);
        i.setRoundName(req.roundName().trim());
        i.setMode(req.mode());
        i.setScheduledAt(req.scheduledAt());
        i.setDurationMinutes(req.durationMinutes());
        i.setLocationOrLink(blankToNull(req.locationOrLink()));
        i.setStatus(InterviewStatus.SCHEDULED);
        for (Long empId : panelIds) {
            Employee panelist = employees.findById(empId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee " + empId + " not found"));
            if (panelist.getStatus() == EmployeeStatus.EXITED) {
                throw new BadRequestException(panelist.getFullName() + " has exited and cannot interview");
            }
            InterviewFeedback f = new InterviewFeedback();
            f.setInterviewer(panelist);
            i.addPanelist(f);
        }
        interviews.save(i);

        if (a.getStage() == ApplicationStage.APPLIED || a.getStage() == ApplicationStage.SCREENING) {
            applicationService.moveTo(a, ApplicationStage.INTERVIEW, null);
        }
        applicationService.log(a, ApplicationEventType.INTERVIEW_SCHEDULED, null, null,
                i.getRoundName() + " scheduled for " + WHEN.format(i.getScheduledAt()),
                applicationService.actorName());
        return RecruitmentMapper.interview(i);
    }

    /** Reschedule, move the link, or mark the round completed / cancelled / no-show. */
    @Transactional
    public InterviewDto update(Long interviewId, InterviewUpdateRequest req) {
        Interview i = find(interviewId);
        if (i.getStatus() == InterviewStatus.CANCELLED) {
            throw new BusinessException("This interview was cancelled");
        }
        StringBuilder change = new StringBuilder();
        if (req.scheduledAt() != null && !req.scheduledAt().equals(i.getScheduledAt())) {
            if (req.scheduledAt().isBefore(Instant.now())) {
                throw new BadRequestException("Reschedule to a time in the future");
            }
            i.setScheduledAt(req.scheduledAt());
            change.append("rescheduled to ").append(WHEN.format(req.scheduledAt()));
        }
        if (req.locationOrLink() != null) {
            i.setLocationOrLink(blankToNull(req.locationOrLink()));
        }
        if (req.status() != null && req.status() != i.getStatus()) {
            i.setStatus(req.status());
            if (!change.isEmpty()) {
                change.append("; ");
            }
            change.append("marked ").append(req.status().name().toLowerCase().replace('_', ' '));
        }
        if (!change.isEmpty()) {
            applicationService.log(i.getApplication(), ApplicationEventType.INTERVIEW_UPDATED, null, null,
                    i.getRoundName() + " " + change, applicationService.actorName());
        }
        return RecruitmentMapper.interview(i);
    }

    /** Only the panelist may submit (or revise) their own scorecard. */
    @Transactional
    public MyInterviewDto submitFeedback(Long interviewId, Long interviewerEmpId, SubmitFeedbackRequest req) {
        Interview i = find(interviewId);
        InterviewFeedback f = feedback.findByInterviewIdAndInterviewerId(interviewId, interviewerEmpId)
                .orElseThrow(() -> new BusinessException("You are not on the panel for this interview"));
        if (i.getStatus() == InterviewStatus.CANCELLED || i.getStatus() == InterviewStatus.NO_SHOW) {
            throw new BusinessException("Feedback cannot be given for a "
                    + i.getStatus().name().toLowerCase().replace('_', ' ') + " interview");
        }
        boolean revision = f.isSubmitted();
        f.setRating(req.rating());
        f.setRecommendation(req.recommendation());
        f.setStrengths(blankToNull(req.strengths()));
        f.setConcerns(blankToNull(req.concerns()));
        f.setSubmittedAt(Instant.now());

        if (i.getStatus() == InterviewStatus.SCHEDULED && i.getFeedback().stream().allMatch(InterviewFeedback::isSubmitted)) {
            i.setStatus(InterviewStatus.COMPLETED);
        }
        applicationService.log(i.getApplication(), ApplicationEventType.FEEDBACK, null, null,
                f.getInterviewer().getFullName() + (revision ? " revised" : " submitted") + " feedback for "
                        + i.getRoundName() + ": " + req.rating() + "/5, "
                        + req.recommendation().name().toLowerCase().replace('_', ' '),
                f.getInterviewer().getFullName());
        return RecruitmentMapper.myInterview(f);
    }

    @Transactional(readOnly = true)
    public List<MyInterviewDto> mine(Long interviewerEmpId) {
        return feedback.findForInterviewer(interviewerEmpId).stream().map(RecruitmentMapper::myInterview).toList();
    }

    private Interview find(Long id) {
        return interviews.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview " + id + " not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
