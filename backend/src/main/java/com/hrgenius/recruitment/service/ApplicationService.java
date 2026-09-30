package com.hrgenius.recruitment.service;

import com.hrgenius.auth.entity.User;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.*;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationEventType;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import com.hrgenius.recruitment.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A candidate's journey through one requisition's pipeline. Every stage move and note is written
 * to the application's activity trail. HIRED is reachable only through the hire flow.
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final InterviewRepository interviews;
    private final OfferRepository offers;
    private final RequisitionService requisitionService;
    private final CandidateService candidateService;
    private final CurrentUserService currentUser;

    public ApplicationService(JobApplicationRepository applications, ApplicationEventRepository events,
                              InterviewRepository interviews, OfferRepository offers,
                              RequisitionService requisitionService, CandidateService candidateService,
                              CurrentUserService currentUser) {
        this.applications = applications;
        this.events = events;
        this.interviews = interviews;
        this.offers = offers;
        this.requisitionService = requisitionService;
        this.candidateService = candidateService;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PipelineDto pipeline(Long requisitionId) {
        JobRequisition r = requisitionService.find(requisitionId);
        List<ApplicationCardDto> cards = applications.findPipeline(requisitionId).stream()
                .map(a -> RecruitmentMapper.card(a, interviews.findByApplicationIdOrderByScheduledAtAsc(a.getId())))
                .toList();
        return new PipelineDto(requisitionService.toDto(r), cards);
    }

    @Transactional(readOnly = true)
    public ApplicationDetailDto detail(Long id) {
        return toDetail(find(id));
    }

    // ------------------------------------------------------------------ writes

    /** Adds an existing candidate to an OPEN requisition's pipeline at APPLIED. */
    @Transactional
    public ApplicationDetailDto create(CreateApplicationRequest req) {
        JobRequisition r = requisitionService.find(req.requisitionId());
        Candidate c = candidateService.find(req.candidateId());
        JobApplication a = open(r, c, req.source() != null ? req.source() : c.getSource(), req.coverNote(), actorName());
        return toDetail(a);
    }

    /**
     * Creates the application row and its first trail entry. Shared with the public careers flow,
     * which passes its own actor name because nobody is logged in.
     */
    JobApplication open(JobRequisition r, Candidate c, CandidateSource source, String coverNote, String actor) {
        if (r.getStatus() != RequisitionStatus.OPEN) {
            throw new BusinessException(r.getReqCode() + " is not open for applications");
        }
        if (applications.existsByRequisitionIdAndCandidateId(r.getId(), c.getId())) {
            throw new BusinessException(c.getFullName() + " has already applied to " + r.getReqCode());
        }
        JobApplication a = new JobApplication();
        a.setRequisition(r);
        a.setCandidate(c);
        a.setStage(ApplicationStage.APPLIED);
        a.setStageChangedAt(Instant.now());
        a.setSource(source == null ? CandidateSource.DIRECT : source);
        a.setCoverNote(coverNote == null || coverNote.isBlank() ? null : coverNote.trim());
        applications.save(a);
        log(a, ApplicationEventType.STAGE_CHANGED, null, ApplicationStage.APPLIED, "Application received", actor);
        return a;
    }

    @Transactional
    public ApplicationDetailDto moveStage(Long id, MoveStageRequest req) {
        JobApplication a = find(id);
        moveTo(a, req.stage(), req.note());
        return toDetail(a);
    }

    /**
     * Stage-move rules: HIRED only via the hire flow; a hired application is final; rejecting needs a
     * reason; a rejected or withdrawn application may be reopened into an active stage.
     */
    void moveTo(JobApplication a, ApplicationStage target, String note) {
        ApplicationStage from = a.getStage();
        if (target == from) {
            return;
        }
        if (target == ApplicationStage.HIRED) {
            throw new BusinessException("Use \"Convert to employee\" on an accepted offer to hire a candidate");
        }
        if (from == ApplicationStage.HIRED) {
            throw new BusinessException("This candidate has already been hired");
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        if (target == ApplicationStage.REJECTED && trimmed == null) {
            throw new BadRequestException("Give a reason when rejecting a candidate");
        }
        a.setStage(target);
        a.setStageChangedAt(Instant.now());
        a.setRejectionReason(target == ApplicationStage.REJECTED ? trimmed : null);
        String message = "Moved to " + target.name().toLowerCase() + (trimmed != null ? ": " + trimmed : "");
        log(a, ApplicationEventType.STAGE_CHANGED, from, target, message, actorName());
    }

    @Transactional
    public ApplicationDetailDto addNote(Long id, NoteRequest req) {
        JobApplication a = find(id);
        log(a, ApplicationEventType.NOTE, null, null, req.message().trim(), actorName());
        return toDetail(a);
    }

    // ------------------------------------------------------------------ helpers

    void log(JobApplication a, ApplicationEventType type, ApplicationStage from, ApplicationStage to,
             String message, String actor) {
        ApplicationEvent e = new ApplicationEvent();
        e.setApplicationId(a.getId());
        e.setEventType(type);
        e.setFromStage(from);
        e.setToStage(to);
        e.setMessage(message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
        e.setActorName(actor);
        events.save(e);
    }

    String actorName() {
        return currentUser.user().map(User::getFullName).filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> currentUser.email() != null ? currentUser.email() : "System");
    }

    JobApplication find(Long id) {
        return applications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application " + id + " not found"));
    }

    ApplicationDetailDto toDetail(JobApplication a) {
        List<ApplicationEventDto> trail = events.findByApplicationIdOrderByIdDesc(a.getId()).stream()
                .map(RecruitmentMapper::event).toList();
        List<InterviewDto> rounds = interviews.findByApplicationIdOrderByScheduledAtAsc(a.getId()).stream()
                .map(RecruitmentMapper::interview).toList();
        List<OfferDto> offerDtos = offers.findByApplicationIdOrderByIdDesc(a.getId()).stream()
                .map(RecruitmentMapper::offer).toList();
        return new ApplicationDetailDto(a.getId(), requisitionService.toDto(a.getRequisition()),
                candidateService.toDto(a.getCandidate()), a.getStage().name(), a.getStageChangedAt(),
                a.getSource().name(), a.getCoverNote(), a.getRejectionReason(), a.getEmployeeId(),
                trail, rounds, offerDtos, a.getCreatedAt());
    }

    /** Stage -> count for a requisition, used by summaries. */
    @Transactional(readOnly = true)
    public Map<String, Long> stageCounts(Long requisitionId) {
        return applications.findPipeline(requisitionId).stream()
                .collect(Collectors.groupingBy(a -> a.getStage().name(), Collectors.counting()));
    }
}
