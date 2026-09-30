package com.hrgenius.recruitment.service;

import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.service.ApprovalOutcomeHandler;
import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.approval.service.Approver;
import com.hrgenius.approval.service.ApproverResolver;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.org.dto.OrgDtos.CompanyDto;
import com.hrgenius.org.repository.DepartmentRepository;
import com.hrgenius.org.repository.DesignationRepository;
import com.hrgenius.org.repository.GradeRepository;
import com.hrgenius.org.repository.LocationRepository;
import com.hrgenius.org.service.CompanyService;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.Candidate;
import com.hrgenius.recruitment.entity.JobApplication;
import com.hrgenius.recruitment.entity.Offer;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationEventType;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.OfferStatus;
import com.hrgenius.recruitment.repository.OfferRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Offer lifecycle: DRAFT -> PENDING_APPROVAL -> APPROVED -> SENT -> ACCEPTED / DECLINED.
 * Approval (hiring manager, then an HR account with RECRUITMENT_APPROVE) runs through the engine.
 */
@Service
public class OfferService implements ApprovalOutcomeHandler {

    public static final String SUBJECT_TYPE = "OFFER";

    /** Statuses that count as "the" live offer for an application; only one may exist. */
    static final Set<OfferStatus> LIVE = EnumSet.of(OfferStatus.DRAFT, OfferStatus.PENDING_APPROVAL,
            OfferStatus.APPROVED, OfferStatus.SENT, OfferStatus.ACCEPTED);
    private static final Set<OfferStatus> EDITABLE = EnumSet.of(OfferStatus.DRAFT, OfferStatus.REJECTED);
    private static final Set<OfferStatus> WITHDRAWABLE = EnumSet.of(OfferStatus.DRAFT, OfferStatus.PENDING_APPROVAL,
            OfferStatus.APPROVED, OfferStatus.SENT);

    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final OfferRepository offers;
    private final ApplicationService applicationService;
    private final DesignationRepository designations;
    private final DepartmentRepository departments;
    private final LocationRepository locations;
    private final GradeRepository grades;
    private final ApprovalService approvals;
    private final ApproverResolver approverResolver;
    private final CompanyService companyService;

    public OfferService(OfferRepository offers, ApplicationService applicationService,
                        DesignationRepository designations, DepartmentRepository departments,
                        LocationRepository locations, GradeRepository grades, ApprovalService approvals,
                        ApproverResolver approverResolver, CompanyService companyService) {
        this.offers = offers;
        this.applicationService = applicationService;
        this.designations = designations;
        this.departments = departments;
        this.locations = locations;
        this.grades = grades;
        this.approvals = approvals;
        this.approverResolver = approverResolver;
        this.companyService = companyService;
    }

    // ------------------------------------------------------------------ writes

    /** Drafts an offer; the application moves to the OFFER stage. */
    @Transactional
    public OfferDto create(Long applicationId, OfferRequest req) {
        JobApplication a = applicationService.find(applicationId);
        if (a.getStage().isTerminal()) {
            throw new BusinessException("Cannot make an offer on a " + a.getStage().name().toLowerCase() + " application");
        }
        if (offers.existsByApplicationIdAndStatusIn(applicationId, LIVE)) {
            throw new BusinessException("This candidate already has an active offer; withdraw it first");
        }
        Offer o = new Offer();
        o.setApplication(a);
        apply(o, req);
        o.setStatus(OfferStatus.DRAFT);
        offers.save(o);
        if (a.getStage() != ApplicationStage.OFFER) {
            applicationService.moveTo(a, ApplicationStage.OFFER, null);
        }
        applicationService.log(a, ApplicationEventType.OFFER, null, null,
                "Offer drafted: " + o.getDesignation().getName() + ", " + inr(o.getAnnualCtc()) + " per annum",
                applicationService.actorName());
        return RecruitmentMapper.offer(o);
    }

    @Transactional
    public OfferDto update(Long offerId, OfferRequest req) {
        Offer o = find(offerId);
        if (!EDITABLE.contains(o.getStatus())) {
            throw new BusinessException("Only draft or rejected offers can be edited");
        }
        apply(o, req);
        return RecruitmentMapper.offer(o);
    }

    @Transactional
    public OfferDto submit(Long offerId, Long requesterEmpId) {
        Offer o = find(offerId);
        if (!EDITABLE.contains(o.getStatus())) {
            throw new BusinessException("Only draft or rejected offers can be submitted for approval");
        }
        JobApplication a = o.getApplication();
        o.setStatus(OfferStatus.PENDING_APPROVAL);
        List<Approver> chain = approverResolver.chainOf(requesterEmpId,
                a.getRequisition().getHiringManager().getId(), "HIRING_MANAGER", RequisitionService.APPROVE_PERMISSION);
        String title = "Offer · " + a.getCandidate().getFullName() + " · " + o.getDesignation().getName()
                + " · " + inr(o.getAnnualCtc()) + " p.a., joining " + o.getJoiningDate();
        ApprovalRequest approval = approvals.submit(SUBJECT_TYPE, o.getId(), requesterEmpId, title, chain);
        o.setApprovalRequestId(approval.getId());
        applicationService.log(a, ApplicationEventType.OFFER, null, null, "Offer submitted for approval",
                applicationService.actorName());
        return RecruitmentMapper.offer(o);
    }

    /** APPROVED -> SENT: the letter has gone to the candidate. */
    @Transactional
    public OfferDto send(Long offerId) {
        Offer o = find(offerId);
        if (o.getStatus() != OfferStatus.APPROVED) {
            throw new BusinessException("Only an approved offer can be sent");
        }
        o.setStatus(OfferStatus.SENT);
        o.setSentAt(Instant.now());
        applicationService.log(o.getApplication(), ApplicationEventType.OFFER, null, null, "Offer letter sent",
                applicationService.actorName());
        return RecruitmentMapper.offer(o);
    }

    /** Records the candidate's answer to a SENT offer. */
    @Transactional
    public OfferDto respond(Long offerId, OfferResponseRequest req) {
        Offer o = find(offerId);
        if (o.getStatus() != OfferStatus.SENT) {
            throw new BusinessException("Only a sent offer can be accepted or declined");
        }
        boolean accepted = req.accepted();
        String reason = req.declineReason() == null || req.declineReason().isBlank() ? null : req.declineReason().trim();
        o.setStatus(accepted ? OfferStatus.ACCEPTED : OfferStatus.DECLINED);
        o.setRespondedAt(Instant.now());
        o.setDeclineReason(accepted ? null : reason);
        applicationService.log(o.getApplication(), ApplicationEventType.OFFER, null, null,
                accepted ? "Candidate accepted the offer" : "Candidate declined the offer" + (reason != null ? ": " + reason : ""),
                applicationService.actorName());
        return RecruitmentMapper.offer(o);
    }

    @Transactional
    public OfferDto withdraw(Long offerId) {
        Offer o = find(offerId);
        if (!WITHDRAWABLE.contains(o.getStatus())) {
            throw new BusinessException("A " + o.getStatus().name().toLowerCase().replace('_', ' ')
                    + " offer cannot be withdrawn");
        }
        if (o.getStatus() == OfferStatus.PENDING_APPROVAL) {
            approvals.cancelBySubject(SUBJECT_TYPE, o.getId(), "Offer withdrawn");
        }
        o.setStatus(OfferStatus.WITHDRAWN);
        applicationService.log(o.getApplication(), ApplicationEventType.OFFER, null, null, "Offer withdrawn",
                applicationService.actorName());
        return RecruitmentMapper.offer(o);
    }

    // --------------------------------------------------- approval callbacks

    @Override
    public String subjectType() {
        return SUBJECT_TYPE;
    }

    @Override
    @Transactional
    public void onApproved(ApprovalRequest request) {
        offers.findById(request.getSubjectId())
                .filter(o -> o.getStatus() == OfferStatus.PENDING_APPROVAL)
                .ifPresent(o -> {
                    o.setStatus(OfferStatus.APPROVED);
                    applicationService.log(o.getApplication(), ApplicationEventType.OFFER, null, null,
                            "Offer approved", "Approval workflow");
                });
    }

    @Override
    @Transactional
    public void onRejected(ApprovalRequest request, String reason) {
        offers.findById(request.getSubjectId())
                .filter(o -> o.getStatus() == OfferStatus.PENDING_APPROVAL)
                .ifPresent(o -> {
                    o.setStatus(OfferStatus.REJECTED);
                    applicationService.log(o.getApplication(), ApplicationEventType.OFFER, null, null,
                            "Offer rejected in approval" + (reason != null && !reason.isBlank() ? ": " + reason : ""),
                            "Approval workflow");
                });
    }

    // ------------------------------------------------------------------ letter

    /** Renders the offer letter as a PDF. Drafts carry a "DRAFT" marker in the heading. */
    @Transactional(readOnly = true)
    public OfferLetter letter(Long offerId) {
        Offer o = find(offerId);
        Candidate c = o.getApplication().getCandidate();
        CompanyDto company = companyService.get();
        String companyName = Optional.ofNullable(company.legalName()).filter(s -> !s.isBlank()).orElse(company.name());
        boolean draft = o.getStatus() == OfferStatus.DRAFT || o.getStatus() == OfferStatus.PENDING_APPROVAL
                || o.getStatus() == OfferStatus.REJECTED;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 64, 56);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);

        doc.add(new Paragraph(companyName, title));
        if (company.website() != null) {
            doc.add(new Paragraph(company.website(), small));
        }
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph((draft ? "DRAFT — " : "") + "Letter of Offer", h));
        doc.add(new Paragraph(LETTER_DATE.format(LocalDate.now()), body));
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("Dear " + c.getFirstName() + ",", body));
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("We are delighted to offer you the position of " + o.getDesignation().getName()
                + " in our " + o.getDepartment().getName() + " team, based in " + o.getLocation().getName()
                + ". The key terms of this offer are:", body));
        doc.add(Chunk.NEWLINE);

        com.lowagie.text.List terms = new com.lowagie.text.List(false, 14);
        terms.add(new ListItem("Role: " + o.getDesignation().getName()
                + (o.getGrade() != null ? " (" + o.getGrade().getName() + ")" : ""), body));
        terms.add(new ListItem("Employment type: " + o.getEmploymentType().name().replace('_', ' ').toLowerCase(), body));
        terms.add(new ListItem("Annual cost to company: " + inr(o.getAnnualCtc()), body));
        terms.add(new ListItem("Date of joining: " + LETTER_DATE.format(o.getJoiningDate()), body));
        terms.add(new ListItem("Location: " + o.getLocation().getName(), body));
        doc.add(terms);
        doc.add(Chunk.NEWLINE);

        if (o.getExpiryDate() != null) {
            doc.add(new Paragraph("Please confirm your acceptance by " + LETTER_DATE.format(o.getExpiryDate())
                    + ", after which this offer lapses.", body));
            doc.add(Chunk.NEWLINE);
        }
        doc.add(new Paragraph("This offer is subject to satisfactory background verification and submission of "
                + "the joining documents listed in your onboarding checklist.", body));
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("We look forward to welcoming you.", body));
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("For " + companyName, body));
        doc.add(new Paragraph("Human Resources", body));
        doc.close();

        String fileName = "Offer-" + c.getFirstName() + "-" + c.getLastName() + (draft ? "-DRAFT" : "") + ".pdf";
        return new OfferLetter(out.toByteArray(), CandidateService.safeFileName(fileName));
    }

    // ------------------------------------------------------------------ helpers

    Offer find(Long id) {
        return offers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Offer " + id + " not found"));
    }

    private void apply(Offer o, OfferRequest req) {
        if (req.expiryDate() != null && req.expiryDate().isAfter(req.joiningDate())) {
            throw new BadRequestException("The offer should expire on or before the joining date");
        }
        if (req.joiningDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Joining date cannot be in the past");
        }
        o.setDesignation(designations.findById(req.designationId())
                .orElseThrow(() -> new ResourceNotFoundException("Designation " + req.designationId() + " not found")));
        o.setDepartment(departments.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department " + req.departmentId() + " not found")));
        o.setLocation(locations.findById(req.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location " + req.locationId() + " not found")));
        o.setGrade(req.gradeId() == null ? null : grades.findById(req.gradeId())
                .orElseThrow(() -> new ResourceNotFoundException("Grade " + req.gradeId() + " not found")));
        o.setEmploymentType(req.employmentType());
        o.setAnnualCtc(req.annualCtc());
        o.setJoiningDate(req.joiningDate());
        o.setExpiryDate(req.expiryDate());
        o.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
    }

    /**
     * Indian-grouped rupee amount, e.g. "INR 30,00,000". Done by hand because java.text's
     * DecimalFormat only supports uniform grouping and would print 3,000,000.
     */
    static String inr(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        String digits = amount.setScale(0, RoundingMode.HALF_UP).abs().toPlainString();
        StringBuilder sb = new StringBuilder();
        int n = digits.length();
        if (n <= 3) {
            sb.append(digits);
        } else {
            String head = digits.substring(0, n - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    sb.append(',');
                }
                sb.append(head.charAt(i));
            }
            sb.append(',').append(digits, n - 3, n);
        }
        return "INR " + (amount.signum() < 0 ? "-" : "") + sb;
    }

    public record OfferLetter(byte[] content, String fileName) {
    }
}
