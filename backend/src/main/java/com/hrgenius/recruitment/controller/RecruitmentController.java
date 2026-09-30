package com.hrgenius.recruitment.controller;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.web.Downloads;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import com.hrgenius.recruitment.service.*;
import com.hrgenius.recruitment.service.CandidateService.DownloadedResume;
import com.hrgenius.recruitment.service.OfferService.OfferLetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Recruitment (ATS) API. Everything is gated by RECRUITMENT_MANAGE except the panelist endpoints
 * (my interviews, submit feedback) and resume download, which checks panel/hiring-manager access
 * itself. Converting a hire additionally needs EMPLOYEE_WRITE because it creates an employee.
 */
@Tag(name = "Recruitment")
@RestController
@RequestMapping("/api/v1/recruitment")
public class RecruitmentController {

    private static final String MANAGE = "hasAuthority('RECRUITMENT_MANAGE')";

    private final RequisitionService requisitions;
    private final CandidateService candidates;
    private final ApplicationService applications;
    private final InterviewService interviews;
    private final OfferService offers;
    private final HireService hires;
    private final CurrentUserService currentUser;

    public RecruitmentController(RequisitionService requisitions, CandidateService candidates,
                                 ApplicationService applications, InterviewService interviews,
                                 OfferService offers, HireService hires, CurrentUserService currentUser) {
        this.requisitions = requisitions;
        this.candidates = candidates;
        this.applications = applications;
        this.interviews = interviews;
        this.offers = offers;
        this.hires = hires;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ requisitions

    @Operation(summary = "List requisitions")
    @GetMapping("/requisitions")
    @PreAuthorize(MANAGE)
    public PageResponse<RequisitionDto> listRequisitions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) Long departmentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return requisitions.list(new RequisitionFilter(search, status, departmentId), pageable);
    }

    @Operation(summary = "Get a requisition")
    @GetMapping("/requisitions/{id}")
    @PreAuthorize(MANAGE)
    public RequisitionDto getRequisition(@PathVariable Long id) {
        return requisitions.get(id);
    }

    @Operation(summary = "Create a requisition (starts as DRAFT)")
    @PostMapping("/requisitions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public RequisitionDto createRequisition(@Valid @RequestBody RequisitionRequest req) {
        return requisitions.create(req);
    }

    @Operation(summary = "Edit a requisition")
    @PutMapping("/requisitions/{id}")
    @PreAuthorize(MANAGE)
    public RequisitionDto updateRequisition(@PathVariable Long id, @Valid @RequestBody RequisitionRequest req) {
        return requisitions.update(id, req);
    }

    @Operation(summary = "Submit a draft/rejected requisition for approval")
    @PostMapping("/requisitions/{id}/submit")
    @PreAuthorize(MANAGE)
    public RequisitionDto submitRequisition(@PathVariable Long id) {
        return requisitions.submit(id, me());
    }

    @Operation(summary = "Hold, reopen, close or cancel a requisition")
    @PostMapping("/requisitions/{id}/status")
    @PreAuthorize(MANAGE)
    public RequisitionDto requisitionStatus(@PathVariable Long id, @Valid @RequestBody RequisitionStatusRequest req) {
        return requisitions.changeStatus(id, req.status());
    }

    @Operation(summary = "A requisition's pipeline (all applications as board cards)")
    @GetMapping("/requisitions/{id}/pipeline")
    @PreAuthorize(MANAGE)
    public PipelineDto pipeline(@PathVariable Long id) {
        return applications.pipeline(id);
    }

    // ------------------------------------------------------------ candidates

    @Operation(summary = "Search the talent pool")
    @GetMapping("/candidates")
    @PreAuthorize(MANAGE)
    public PageResponse<CandidateSummaryDto> listCandidates(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CandidateSource source,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return candidates.list(new CandidateFilter(search, source), pageable);
    }

    @Operation(summary = "Get a candidate with their applications")
    @GetMapping("/candidates/{id}")
    @PreAuthorize(MANAGE)
    public CandidateDto getCandidate(@PathVariable Long id) {
        return candidates.get(id);
    }

    @Operation(summary = "Add a candidate")
    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public CandidateDto createCandidate(@Valid @RequestBody CandidateRequest req) {
        return candidates.create(req);
    }

    @Operation(summary = "Edit a candidate")
    @PutMapping("/candidates/{id}")
    @PreAuthorize(MANAGE)
    public CandidateDto updateCandidate(@PathVariable Long id, @Valid @RequestBody CandidateRequest req) {
        return candidates.update(id, req);
    }

    @Operation(summary = "Upload or replace a candidate's resume (PDF/DOCX)")
    @PostMapping(value = "/candidates/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public CandidateDto uploadResume(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return candidates.uploadResume(id, file);
    }

    @Operation(summary = "Download a resume (recruiters, the candidate's panelists and hiring managers)")
    @GetMapping("/candidates/{id}/resume")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long id) {
        DownloadedResume r = candidates.downloadResume(id);
        return Downloads.attachment(r.resource(), r.fileName(), MediaType.parseMediaType(r.contentType()));
    }

    // ------------------------------------------------------------ applications

    @Operation(summary = "Add a candidate to a requisition's pipeline")
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApplicationDetailDto createApplication(@Valid @RequestBody CreateApplicationRequest req) {
        return applications.create(req);
    }

    @Operation(summary = "Application detail: candidate, activity, interviews and offers")
    @GetMapping("/applications/{id}")
    @PreAuthorize(MANAGE)
    public ApplicationDetailDto getApplication(@PathVariable Long id) {
        return applications.detail(id);
    }

    @Operation(summary = "Move an application to another stage")
    @PostMapping("/applications/{id}/stage")
    @PreAuthorize(MANAGE)
    public ApplicationDetailDto moveStage(@PathVariable Long id, @Valid @RequestBody MoveStageRequest req) {
        return applications.moveStage(id, req);
    }

    @Operation(summary = "Add a note to an application's activity trail")
    @PostMapping("/applications/{id}/notes")
    @PreAuthorize(MANAGE)
    public ApplicationDetailDto addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return applications.addNote(id, req);
    }

    // ------------------------------------------------------------ interviews

    @Operation(summary = "Schedule an interview round with a panel")
    @PostMapping("/applications/{id}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public InterviewDto schedule(@PathVariable Long id, @Valid @RequestBody ScheduleInterviewRequest req) {
        return interviews.schedule(id, req);
    }

    @Operation(summary = "Reschedule or change the status of an interview")
    @PatchMapping("/interviews/{id}")
    @PreAuthorize(MANAGE)
    public InterviewDto updateInterview(@PathVariable Long id, @Valid @RequestBody InterviewUpdateRequest req) {
        return interviews.update(id, req);
    }

    @Operation(summary = "Interviews I sit on, with my scorecard")
    @GetMapping("/interviews/mine")
    public List<MyInterviewDto> myInterviews() {
        return interviews.mine(me());
    }

    @Operation(summary = "Submit or revise my scorecard for an interview")
    @PutMapping("/interviews/{id}/feedback")
    public MyInterviewDto submitFeedback(@PathVariable Long id, @Valid @RequestBody SubmitFeedbackRequest req) {
        return interviews.submitFeedback(id, me(), req);
    }

    // ------------------------------------------------------------ offers

    @Operation(summary = "Draft an offer for an application")
    @PostMapping("/applications/{id}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public OfferDto createOffer(@PathVariable Long id, @Valid @RequestBody OfferRequest req) {
        return offers.create(id, req);
    }

    @Operation(summary = "Edit a draft or rejected offer")
    @PutMapping("/offers/{id}")
    @PreAuthorize(MANAGE)
    public OfferDto updateOffer(@PathVariable Long id, @Valid @RequestBody OfferRequest req) {
        return offers.update(id, req);
    }

    @Operation(summary = "Submit an offer for approval")
    @PostMapping("/offers/{id}/submit")
    @PreAuthorize(MANAGE)
    public OfferDto submitOffer(@PathVariable Long id) {
        return offers.submit(id, me());
    }

    @Operation(summary = "Mark an approved offer as sent to the candidate")
    @PostMapping("/offers/{id}/send")
    @PreAuthorize(MANAGE)
    public OfferDto sendOffer(@PathVariable Long id) {
        return offers.send(id);
    }

    @Operation(summary = "Record the candidate's acceptance or decline")
    @PostMapping("/offers/{id}/response")
    @PreAuthorize(MANAGE)
    public OfferDto respond(@PathVariable Long id, @Valid @RequestBody OfferResponseRequest req) {
        return offers.respond(id, req);
    }

    @Operation(summary = "Withdraw an offer")
    @PostMapping("/offers/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public OfferDto withdraw(@PathVariable Long id) {
        return offers.withdraw(id);
    }

    @Operation(summary = "Download the offer letter PDF")
    @GetMapping("/offers/{id}/letter")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Resource> letter(@PathVariable Long id) {
        OfferLetter letter = offers.letter(id);
        return Downloads.attachment(letter.content(), letter.fileName(), MediaType.APPLICATION_PDF);
    }

    @Operation(summary = "Convert an accepted offer into an employee (+ login and onboarding)")
    @PostMapping("/offers/{id}/hire")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE + " and hasAuthority('EMPLOYEE_WRITE')")
    public HireResponse hire(@PathVariable Long id, @Valid @RequestBody HireRequest req) {
        return hires.hire(id, req, currentUser.employeeId().orElse(null));
    }

    // ------------------------------------------------------------ helpers

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
