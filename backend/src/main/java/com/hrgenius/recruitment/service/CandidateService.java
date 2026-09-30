package com.hrgenius.recruitment.service;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.storage.FileStorageService;
import com.hrgenius.common.util.SearchPredicates;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.Candidate;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import com.hrgenius.recruitment.repository.CandidateRepository;
import com.hrgenius.recruitment.repository.InterviewFeedbackRepository;
import com.hrgenius.recruitment.repository.JobApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The talent pool: candidate profiles and their resumes. */
@Service
public class CandidateService {

    /** Resumes are narrower than general documents: PDF or DOCX only. */
    static final Set<String> RESUME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    static final String MANAGE_PERMISSION = "RECRUITMENT_MANAGE";

    private final CandidateRepository candidates;
    private final JobApplicationRepository applications;
    private final InterviewFeedbackRepository feedback;
    private final EmployeeRepository employees;
    private final FileStorageService storage;
    private final CurrentUserService currentUser;

    public CandidateService(CandidateRepository candidates, JobApplicationRepository applications,
                            InterviewFeedbackRepository feedback, EmployeeRepository employees,
                            FileStorageService storage, CurrentUserService currentUser) {
        this.candidates = candidates;
        this.applications = applications;
        this.feedback = feedback;
        this.employees = employees;
        this.storage = storage;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateSummaryDto> list(CandidateFilter f, Pageable pageable) {
        return PageResponse.from(candidates.findAll(spec(f), pageable)
                .map(c -> RecruitmentMapper.candidateSummary(c, applications.findByCandidate(c.getId()).size())));
    }

    @Transactional(readOnly = true)
    public CandidateDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public CandidateDto create(CandidateRequest req) {
        if (candidates.findByEmailIgnoreCase(req.email().trim()).isPresent()) {
            throw new BusinessException("A candidate with email " + req.email() + " already exists");
        }
        Candidate c = new Candidate();
        apply(c, req);
        candidates.save(c);
        return toDto(c);
    }

    @Transactional
    public CandidateDto update(Long id, CandidateRequest req) {
        Candidate c = find(id);
        if (candidates.existsByEmailIgnoreCaseAndIdNot(req.email().trim(), id)) {
            throw new BusinessException("Another candidate already uses email " + req.email());
        }
        apply(c, req);
        return toDto(c);
    }

    @Transactional
    public CandidateDto uploadResume(Long id, MultipartFile file) {
        Candidate c = find(id);
        attachResume(c, file);
        return toDto(c);
    }

    /** Validates and stores a resume on the candidate, replacing (and deleting) any previous one. */
    void attachResume(Candidate c, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a resume file to upload");
        }
        if (!RESUME_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Resumes must be PDF or DOCX");
        }
        String key;
        try {
            key = storage.store(file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        String previous = c.getResumeKey();
        c.setResumeKey(key);
        c.setResumeFileName(safeFileName(file.getOriginalFilename()));
        c.setResumeContentType(file.getContentType());
        c.setResumeSize(file.getSize());
        if (previous != null) {
            storage.delete(previous);
        }
    }

    /**
     * Resume download. Allowed for recruiters, and for employees who sit on one of the candidate's
     * interview panels or are the hiring manager of a requisition the candidate applied to.
     */
    @Transactional(readOnly = true)
    public DownloadedResume downloadResume(Long id) {
        Candidate c = find(id);
        if (!canViewResume(c)) {
            throw new AccessDeniedException("Not allowed to view this resume");
        }
        if (c.getResumeKey() == null) {
            throw new ResourceNotFoundException("This candidate has no resume on file");
        }
        return new DownloadedResume(storage.load(c.getResumeKey()), c.getResumeFileName(), c.getResumeContentType());
    }

    private boolean canViewResume(Candidate c) {
        if (currentUser.hasAuthority(MANAGE_PERMISSION)) {
            return true;
        }
        Long me = currentUser.employeeId().orElse(null);
        if (me == null) {
            return false;
        }
        if (feedback.countPanelSeats(me, c.getId()) > 0) {
            return true;
        }
        return applications.findByCandidate(c.getId()).stream()
                .anyMatch(a -> me.equals(a.getRequisition().getHiringManager().getId()));
    }

    Candidate find(Long id) {
        return candidates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate " + id + " not found"));
    }

    CandidateDto toDto(Candidate c) {
        List<CandidateApplicationDto> apps = applications.findByCandidate(c.getId()).stream()
                .map(RecruitmentMapper::candidateApplication).toList();
        return RecruitmentMapper.candidate(c, apps);
    }

    private void apply(Candidate c, CandidateRequest r) {
        c.setFirstName(r.firstName().trim());
        c.setLastName(r.lastName().trim());
        c.setEmail(r.email().trim().toLowerCase());
        c.setPhone(blankToNull(r.phone()));
        c.setCurrentCompany(blankToNull(r.currentCompany()));
        c.setCurrentTitle(blankToNull(r.currentTitle()));
        c.setTotalExperience(r.totalExperience());
        c.setCurrentCtc(r.currentCtc());
        c.setExpectedCtc(r.expectedCtc());
        c.setNoticePeriodDays(r.noticePeriodDays());
        c.setCity(blankToNull(r.city()));
        c.setLinkedinUrl(blankToNull(r.linkedinUrl()));
        c.setSource(r.source() == null ? CandidateSource.DIRECT : r.source());
        c.setReferredBy(r.referredById() == null ? null : employees.findById(r.referredById())
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + r.referredById() + " not found")));
        c.setNotes(blankToNull(r.notes()));
    }

    private static Specification<Candidate> spec(CandidateFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.search() != null && !f.search().isBlank()) {
                p.add(cb.or(
                        SearchPredicates.containsIgnoreCase(cb, root.get("firstName"), f.search()),
                        SearchPredicates.containsIgnoreCase(cb, root.get("lastName"), f.search()),
                        SearchPredicates.containsIgnoreCase(cb, root.get("email"), f.search()),
                        SearchPredicates.containsIgnoreCase(cb, root.get("currentCompany"), f.search()),
                        SearchPredicates.containsIgnoreCase(cb, root.get("currentTitle"), f.search())));
            }
            if (f.source() != null) {
                p.add(cb.equal(root.get("source"), f.source()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** Keeps only a display-safe basename; the stored key never derives from it. */
    static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "resume";
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record DownloadedResume(Resource resource, String fileName, String contentType) {
    }
}
