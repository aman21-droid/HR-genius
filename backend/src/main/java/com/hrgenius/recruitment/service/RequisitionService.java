package com.hrgenius.recruitment.service;

import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.service.ApprovalOutcomeHandler;
import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.approval.service.Approver;
import com.hrgenius.approval.service.ApproverResolver;
import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.util.SearchPredicates;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.org.repository.DepartmentRepository;
import com.hrgenius.org.repository.DesignationRepository;
import com.hrgenius.org.repository.GradeRepository;
import com.hrgenius.org.repository.LocationRepository;
import com.hrgenius.recruitment.dto.RecruitmentDtos.*;
import com.hrgenius.recruitment.entity.JobRequisition;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import com.hrgenius.recruitment.repository.JobApplicationRepository;
import com.hrgenius.recruitment.repository.JobRequisitionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Requisition lifecycle. Submitting opens an approval flow (hiring manager, then an HR account
 * with RECRUITMENT_APPROVE); the engine calls back here to open or reject the requisition.
 */
@Service
public class RequisitionService implements ApprovalOutcomeHandler {

    public static final String SUBJECT_TYPE = "REQUISITION";
    static final String APPROVE_PERMISSION = "RECRUITMENT_APPROVE";

    /** Manual transitions allowed from each status (approval-driven ones are handled separately). */
    private static final Map<RequisitionStatus, Set<RequisitionStatus>> MANUAL_TRANSITIONS = Map.of(
            RequisitionStatus.DRAFT, EnumSet.of(RequisitionStatus.CANCELLED),
            RequisitionStatus.REJECTED, EnumSet.of(RequisitionStatus.CANCELLED),
            RequisitionStatus.OPEN, EnumSet.of(RequisitionStatus.ON_HOLD, RequisitionStatus.CLOSED),
            RequisitionStatus.ON_HOLD, EnumSet.of(RequisitionStatus.OPEN, RequisitionStatus.CLOSED));

    private final JobRequisitionRepository requisitions;
    private final JobApplicationRepository applications;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final LocationRepository locations;
    private final GradeRepository grades;
    private final EmployeeRepository employees;
    private final ApprovalService approvals;
    private final ApproverResolver approverResolver;

    public RequisitionService(JobRequisitionRepository requisitions, JobApplicationRepository applications,
                              DepartmentRepository departments, DesignationRepository designations,
                              LocationRepository locations, GradeRepository grades, EmployeeRepository employees,
                              ApprovalService approvals, ApproverResolver approverResolver) {
        this.requisitions = requisitions;
        this.applications = applications;
        this.departments = departments;
        this.designations = designations;
        this.locations = locations;
        this.grades = grades;
        this.employees = employees;
        this.approvals = approvals;
        this.approverResolver = approverResolver;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PageResponse<RequisitionDto> list(RequisitionFilter f, Pageable pageable) {
        Page<JobRequisition> page = requisitions.findAll(spec(f), pageable);
        Map<Long, Long> active = activeCounts();
        return PageResponse.from(page.map(r -> RecruitmentMapper.requisition(r, active.getOrDefault(r.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public RequisitionDto get(Long id) {
        return toDto(find(id));
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public RequisitionDto create(RequisitionRequest req) {
        JobRequisition r = new JobRequisition();
        r.setReqCode("REQ-" + String.format("%04d", requisitions.nextCodeNumber()));
        apply(r, req);
        r.setStatus(RequisitionStatus.DRAFT);
        requisitions.save(r);
        return toDto(r);
    }

    @Transactional
    public RequisitionDto update(Long id, RequisitionRequest req) {
        JobRequisition r = find(id);
        if (r.getStatus() == RequisitionStatus.PENDING_APPROVAL) {
            throw new BusinessException("This requisition is awaiting approval and cannot be edited");
        }
        if (r.getStatus() == RequisitionStatus.CLOSED || r.getStatus() == RequisitionStatus.CANCELLED) {
            throw new BusinessException("A " + r.getStatus().name().toLowerCase() + " requisition cannot be edited");
        }
        if (req.openings() < r.getFilled()) {
            throw new BadRequestException("Openings cannot be fewer than the " + r.getFilled() + " already filled");
        }
        apply(r, req);
        return toDto(r);
    }

    /** DRAFT or REJECTED -> PENDING_APPROVAL (or straight to OPEN when nobody needs to approve). */
    @Transactional
    public RequisitionDto submit(Long id, Long requesterEmpId) {
        JobRequisition r = find(id);
        if (r.getStatus() != RequisitionStatus.DRAFT && r.getStatus() != RequisitionStatus.REJECTED) {
            throw new BusinessException("Only draft or rejected requisitions can be submitted for approval");
        }
        r.setStatus(RequisitionStatus.PENDING_APPROVAL);
        List<Approver> chain = approverResolver.chainOf(requesterEmpId, r.getHiringManager().getId(),
                "HIRING_MANAGER", APPROVE_PERMISSION);
        String title = r.getReqCode() + " · " + r.getTitle() + " · " + r.getOpenings() + " opening(s), "
                + r.getDepartment().getName();
        ApprovalRequest approval = approvals.submit(SUBJECT_TYPE, r.getId(), requesterEmpId, title, chain);
        r.setApprovalRequestId(approval.getId());
        return toDto(r);
    }

    @Transactional
    public RequisitionDto changeStatus(Long id, RequisitionStatus target) {
        JobRequisition r = find(id);
        Set<RequisitionStatus> allowed = MANUAL_TRANSITIONS.getOrDefault(r.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException("Cannot move a requisition from " + label(r.getStatus()) + " to " + label(target));
        }
        r.setStatus(target);
        if (target == RequisitionStatus.CLOSED || target == RequisitionStatus.CANCELLED) {
            r.setClosedAt(Instant.now());
        }
        return toDto(r);
    }

    /** Called by the hire flow: counts a hire and auto-closes once every opening is filled. */
    @Transactional
    public void recordHire(JobRequisition r) {
        r.setFilled(r.getFilled() + 1);
        if (r.getFilled() >= r.getOpenings() && r.getStatus() != RequisitionStatus.CLOSED) {
            r.setStatus(RequisitionStatus.CLOSED);
            r.setClosedAt(Instant.now());
        }
    }

    // --------------------------------------------------- approval callbacks

    @Override
    public String subjectType() {
        return SUBJECT_TYPE;
    }

    @Override
    @Transactional
    public void onApproved(ApprovalRequest request) {
        requisitions.findById(request.getSubjectId())
                .filter(r -> r.getStatus() == RequisitionStatus.PENDING_APPROVAL)
                .ifPresent(r -> {
                    r.setStatus(RequisitionStatus.OPEN);
                    r.setOpenedAt(Instant.now());
                });
    }

    @Override
    @Transactional
    public void onRejected(ApprovalRequest request, String reason) {
        requisitions.findById(request.getSubjectId())
                .filter(r -> r.getStatus() == RequisitionStatus.PENDING_APPROVAL)
                .ifPresent(r -> r.setStatus(RequisitionStatus.REJECTED));
    }

    // ------------------------------------------------------------------ helpers

    JobRequisition find(Long id) {
        return requisitions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Requisition " + id + " not found"));
    }

    RequisitionDto toDto(JobRequisition r) {
        long active = applications.countActiveByRequisition(ApplicationStage.TERMINAL).stream()
                .filter(row -> r.getId().equals(row[0]))
                .mapToLong(row -> ((Number) row[1]).longValue())
                .findFirst().orElse(0L);
        return RecruitmentMapper.requisition(r, active);
    }

    private Map<Long, Long> activeCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : applications.countActiveByRequisition(ApplicationStage.TERMINAL)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private void apply(JobRequisition r, RequisitionRequest req) {
        if (req.minExperience() != null && req.maxExperience() != null
                && req.maxExperience().compareTo(req.minExperience()) < 0) {
            throw new BadRequestException("Maximum experience cannot be below the minimum");
        }
        if (req.salaryMin() != null && req.salaryMax() != null && req.salaryMax().compareTo(req.salaryMin()) < 0) {
            throw new BadRequestException("Maximum salary cannot be below the minimum");
        }
        Employee manager = employees.findById(req.hiringManagerId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + req.hiringManagerId() + " not found"));
        if (manager.getStatus() == EmployeeStatus.EXITED) {
            throw new BadRequestException(manager.getFullName() + " has exited and cannot be the hiring manager");
        }
        r.setTitle(req.title().trim());
        r.setDepartment(master(departments.findById(req.departmentId()), "Department", req.departmentId()));
        r.setDesignation(master(designations.findById(req.designationId()), "Designation", req.designationId()));
        r.setLocation(master(locations.findById(req.locationId()), "Location", req.locationId()));
        r.setGrade(req.gradeId() == null ? null : master(grades.findById(req.gradeId()), "Grade", req.gradeId()));
        r.setHiringManager(manager);
        r.setEmploymentType(req.employmentType());
        r.setOpenings(req.openings());
        r.setMinExperience(req.minExperience());
        r.setMaxExperience(req.maxExperience());
        r.setSalaryMin(req.salaryMin());
        r.setSalaryMax(req.salaryMax());
        r.setSkills(blankToNull(req.skills()));
        r.setDescription(blankToNull(req.description()));
        r.setTargetDate(req.targetDate());
        r.setPublishOnCareers(req.publishOnCareers() == null || req.publishOnCareers());
    }

    private static Specification<JobRequisition> spec(RequisitionFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.search() != null && !f.search().isBlank()) {
                p.add(cb.or(
                        SearchPredicates.containsIgnoreCase(cb, root.get("title"), f.search()),
                        SearchPredicates.containsIgnoreCase(cb, root.get("reqCode"), f.search())));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.departmentId() != null) {
                p.add(cb.equal(root.get("department").get("id"), f.departmentId()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static <T> T master(Optional<T> value, String kind, Long id) {
        return value.orElseThrow(() -> new ResourceNotFoundException(kind + " " + id + " not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String label(RequisitionStatus s) {
        return s.name().toLowerCase().replace('_', ' ');
    }
}
