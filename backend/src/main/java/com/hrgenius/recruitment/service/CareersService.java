package com.hrgenius.recruitment.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.error.TooManyRequestsException;
import com.hrgenius.recruitment.dto.RecruitmentDtos.CareerApplyForm;
import com.hrgenius.recruitment.dto.RecruitmentDtos.CareerJobDto;
import com.hrgenius.recruitment.entity.Candidate;
import com.hrgenius.recruitment.entity.JobRequisition;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import com.hrgenius.recruitment.repository.CandidateRepository;
import com.hrgenius.recruitment.repository.JobApplicationRepository;
import com.hrgenius.recruitment.repository.JobRequisitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The unauthenticated careers page. Hardening for a public endpoint:
 * <ul>
 *   <li>per-client rate limit on applications (in-memory, per instance);</li>
 *   <li>a honeypot field bots tend to fill: such submissions are silently dropped;</li>
 *   <li>an identical response whether the email is new, known, or already applied, so the endpoint
 *       cannot be used to discover who is in the talent pool;</li>
 *   <li>an existing candidate's profile is never overwritten from public input (only a missing
 *       resume is filled in), so nobody can tamper with someone else's record by knowing their email;</li>
 *   <li>resumes restricted to PDF/DOCX with magic-byte checks (via FileStorageService).</li>
 * </ul>
 * Only OPEN requisitions flagged {@code publishOnCareers} are exposed, and only public fields.
 */
@Slf4j
@Service
public class CareersService {

    static final String PUBLIC_ACTOR = "Careers page";

    private final JobRequisitionRepository requisitions;
    private final CandidateRepository candidates;
    private final JobApplicationRepository applications;
    private final CandidateService candidateService;
    private final ApplicationService applicationService;
    private final int maxPerWindow;
    private final Cache<String, AtomicInteger> attempts;

    public CareersService(JobRequisitionRepository requisitions, CandidateRepository candidates,
                          JobApplicationRepository applications, CandidateService candidateService,
                          ApplicationService applicationService,
                          @Value("${hrgenius.careers.max-applications-per-hour:5}") int maxPerWindow) {
        this.requisitions = requisitions;
        this.candidates = candidates;
        this.applications = applications;
        this.candidateService = candidateService;
        this.applicationService = applicationService;
        this.maxPerWindow = maxPerWindow;
        this.attempts = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(50_000).build();
    }

    @Transactional(readOnly = true)
    public List<CareerJobDto> jobs() {
        return requisitions.findByStatusAndPublishOnCareersTrueOrderByOpenedAtDesc(RequisitionStatus.OPEN).stream()
                .map(CareersService::toPublic).toList();
    }

    @Transactional(readOnly = true)
    public CareerJobDto job(String reqCode) {
        return toPublic(findPublished(reqCode));
    }

    /**
     * Accepts a public application. Always returns normally (same response) for valid input,
     * regardless of whether the candidate already existed or had already applied.
     */
    @Transactional
    public void apply(String reqCode, CareerApplyForm form, MultipartFile resume, String clientKey) {
        throttle(clientKey);
        JobRequisition r = findPublished(reqCode);
        if (resume == null || resume.isEmpty()) {
            throw new BadRequestException("Please attach your resume (PDF or DOCX)");
        }
        if (form.website() != null && !form.website().isBlank()) {
            log.info("Careers honeypot tripped for {} from {}", reqCode, clientKey);
            return;   // pretend success
        }

        String email = form.email().trim().toLowerCase();
        Candidate c = candidates.findByEmailIgnoreCase(email).orElse(null);
        if (c == null) {
            c = new Candidate();
            c.setFirstName(form.firstName().trim());
            c.setLastName(form.lastName().trim());
            c.setEmail(email);
            c.setPhone(blankToNull(form.phone()));
            c.setCurrentCompany(blankToNull(form.currentCompany()));
            c.setCurrentTitle(blankToNull(form.currentTitle()));
            c.setTotalExperience(form.totalExperience());
            c.setNoticePeriodDays(form.noticePeriodDays());
            c.setCity(blankToNull(form.city()));
            c.setLinkedinUrl(blankToNull(form.linkedinUrl()));
            c.setSource(CandidateSource.CAREERS_PAGE);
            candidateService.attachResume(c, resume);
            candidates.save(c);
        } else if (c.getResumeKey() == null) {
            candidateService.attachResume(c, resume);
        }

        if (applications.existsByRequisitionIdAndCandidateId(r.getId(), c.getId())) {
            return;   // already in the pipeline; same response as a fresh application
        }
        applicationService.open(r, c, CandidateSource.CAREERS_PAGE, form.coverNote(), PUBLIC_ACTOR);
    }

    private void throttle(String clientKey) {
        AtomicInteger count = attempts.get(clientKey == null ? "unknown" : clientKey, k -> new AtomicInteger());
        if (count.incrementAndGet() > maxPerWindow) {
            throw new TooManyRequestsException("Too many applications from your network. Please try again later.");
        }
    }

    private JobRequisition findPublished(String reqCode) {
        return requisitions.findByReqCode(reqCode)
                .filter(r -> r.getStatus() == RequisitionStatus.OPEN && r.isPublishOnCareers())
                .orElseThrow(() -> new ResourceNotFoundException("This job is no longer open"));
    }

    private static CareerJobDto toPublic(JobRequisition r) {
        return new CareerJobDto(r.getReqCode(), r.getTitle(), r.getDepartment().getName(), r.getLocation().getName(),
                r.getEmploymentType().name(), r.getMinExperience(), r.getMaxExperience(), r.getSkills(),
                r.getDescription(), r.getOpenedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
