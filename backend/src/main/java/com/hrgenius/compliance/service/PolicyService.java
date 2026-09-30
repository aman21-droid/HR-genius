package com.hrgenius.compliance.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.compliance.dto.PolicyDtos.*;
import com.hrgenius.compliance.entity.Policy;
import com.hrgenius.compliance.entity.Policy.Status;
import com.hrgenius.compliance.entity.PolicyAcknowledgement;
import com.hrgenius.compliance.repository.PolicyAcknowledgementRepository;
import com.hrgenius.compliance.repository.PolicyRepository;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Policy library. HR (POLICY_MANAGE) drafts, publishes and archives policies; every publish of an
 * edited policy bumps its version, so employees must acknowledge the new wording again.
 */
@Service
public class PolicyService {

    public static final String MANAGE_PERMISSION = "POLICY_MANAGE";

    private final PolicyRepository policies;
    private final PolicyAcknowledgementRepository acks;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public PolicyService(PolicyRepository policies, PolicyAcknowledgementRepository acks, EmployeeRepository employees,
                         CurrentUserService currentUser, AuditService audit) {
        this.policies = policies;
        this.acks = acks;
        this.employees = employees;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- employees

    /** Published policies with the caller's acknowledgement state; HR also gets drafts and counts. */
    @Transactional(readOnly = true)
    public List<PolicyDto> list(Long employeeId) {
        boolean manager = currentUser.hasAuthority(MANAGE_PERMISSION);
        List<Policy> list = manager ? policies.findAllByOrderByTitleAsc() : policies.findByStatusOrderByTitleAsc(Status.PUBLISHED);
        Map<String, PolicyAcknowledgement> mine = employeeId == null ? Map.of()
                : acks.findByEmployeeId(employeeId).stream()
                .collect(Collectors.toMap(a -> a.getPolicyId() + ":" + a.getVersionNo(), a -> a, (a, b) -> a));
        int headcount = manager ? currentEmployees().size() : 0;
        return list.stream().map(p -> {
            PolicyAcknowledgement a = mine.get(p.getId() + ":" + p.getVersionNo());
            Integer ackCount = manager ? acknowledgedCount(p) : null;
            return toDto(p, a, ackCount, manager ? headcount : null);
        }).toList();
    }

    /** Policies requiring acknowledgement that the employee has not yet acknowledged in their current version. */
    @Transactional(readOnly = true)
    public List<PolicyDto> pendingFor(Long employeeId) {
        Set<String> done = acks.findByEmployeeId(employeeId).stream()
                .map(a -> a.getPolicyId() + ":" + a.getVersionNo()).collect(Collectors.toSet());
        return policies.findByStatusOrderByTitleAsc(Status.PUBLISHED).stream()
                .filter(p -> p.isRequiresAck() && !done.contains(p.getId() + ":" + p.getVersionNo()))
                .map(p -> toDto(p, null, null, null)).toList();
    }

    @Transactional
    public PolicyDto acknowledge(Long policyId, Long employeeId) {
        Policy p = find(policyId);
        if (p.getStatus() != Status.PUBLISHED) {
            throw new BusinessException("Only published policies can be acknowledged");
        }
        PolicyAcknowledgement a = acks.findByPolicyIdAndEmployeeIdAndVersionNo(policyId, employeeId, p.getVersionNo())
                .orElseGet(() -> {
                    PolicyAcknowledgement n = new PolicyAcknowledgement();
                    n.setPolicyId(policyId);
                    n.setEmployeeId(employeeId);
                    n.setVersionNo(p.getVersionNo());
                    n.setAcknowledgedAt(Instant.now());
                    return acks.save(n);
                });
        return toDto(p, a, null, null);
    }

    // ---------------------------------------------------------------- HR

    @Transactional
    public PolicyDto create(PolicyRequest req) {
        if (policies.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessException("A policy with code " + req.code() + " already exists");
        }
        Policy p = new Policy();
        p.setCode(req.code());
        apply(p, req);
        p.setStatus(Status.DRAFT);
        policies.save(p);
        return toDto(p, null, 0, currentEmployees().size());
    }

    /**
     * Edits a policy. Editing a published policy's wording makes it a new version immediately
     * (existing acknowledgements stay attached to the old version).
     */
    @Transactional
    public PolicyDto update(Long id, PolicyRequest req) {
        Policy p = find(id);
        if (p.getStatus() == Status.ARCHIVED) {
            throw new BusinessException("Archived policies cannot be edited");
        }
        if (!p.getCode().equals(req.code())) {
            throw new BadRequestException("A policy's code cannot be changed");
        }
        boolean wordingChanged = !Objects.equals(p.getBody(), req.body().trim()) || !Objects.equals(p.getTitle(), req.title().trim());
        apply(p, req);
        if (p.getStatus() == Status.PUBLISHED && wordingChanged) {
            p.setVersionNo(p.getVersionNo() + 1);
            p.setPublishedAt(Instant.now());
            audit.record("Policy", p.getId(), com.hrgenius.compliance.entity.AuditLog.AuditAction.UPDATE,
                    p.getCode() + " republished as v" + p.getVersionNo());
        }
        return toDto(p, null, acknowledgedCount(p), currentEmployees().size());
    }

    @Transactional
    public PolicyDto publish(Long id) {
        Policy p = find(id);
        if (p.getStatus() != Status.DRAFT) {
            throw new BusinessException("Only a draft policy can be published");
        }
        p.setStatus(Status.PUBLISHED);
        p.setPublishedAt(Instant.now());
        audit.record("Policy", p.getId(), com.hrgenius.compliance.entity.AuditLog.AuditAction.UPDATE,
                p.getCode() + " v" + p.getVersionNo() + " published");
        return toDto(p, null, acknowledgedCount(p), currentEmployees().size());
    }

    @Transactional
    public PolicyDto archive(Long id) {
        Policy p = find(id);
        if (p.getStatus() == Status.ARCHIVED) {
            throw new BusinessException("This policy is already archived");
        }
        p.setStatus(Status.ARCHIVED);
        return toDto(p, null, acknowledgedCount(p), currentEmployees().size());
    }

    /** Who has not acknowledged the current version yet. */
    @Transactional(readOnly = true)
    public ComplianceDto compliance(Long id) {
        Policy p = find(id);
        Set<Long> done = acks.findByPolicyIdAndVersionNo(p.getId(), p.getVersionNo()).stream()
                .map(PolicyAcknowledgement::getEmployeeId).collect(Collectors.toSet());
        List<Employee> current = currentEmployees();
        List<PendingEmployeeDto> pending = current.stream().filter(e -> !done.contains(e.getId()))
                .sorted(Comparator.comparing(Employee::getEmployeeCode))
                .map(e -> new PendingEmployeeDto(e.getId(), e.getEmployeeCode(), e.getFullName(),
                        e.getDepartment() != null ? e.getDepartment().getName() : null))
                .toList();
        int acked = (int) current.stream().filter(e -> done.contains(e.getId())).count();
        return new ComplianceDto(p.getId(), p.getTitle(), p.getVersionNo(), current.size(), acked, pending);
    }

    // ---------------------------------------------------------------- helpers

    private List<Employee> currentEmployees() {
        return employees.findAll().stream().filter(e -> e.getStatus() != EmployeeStatus.EXITED).toList();
    }

    private int acknowledgedCount(Policy p) {
        Set<Long> current = currentEmployees().stream().map(Employee::getId).collect(Collectors.toSet());
        return (int) acks.findByPolicyIdAndVersionNo(p.getId(), p.getVersionNo()).stream()
                .filter(a -> current.contains(a.getEmployeeId())).count();
    }

    private Policy find(Long id) {
        return policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy " + id + " not found"));
    }

    private static void apply(Policy p, PolicyRequest req) {
        p.setTitle(req.title().trim());
        p.setCategory(req.category().trim());
        p.setSummary(req.summary() == null || req.summary().isBlank() ? null : req.summary().trim());
        p.setBody(req.body().trim());
        p.setRequiresAck(req.requiresAck() == null || req.requiresAck());
        p.setEffectiveDate(req.effectiveDate());
    }

    private static PolicyDto toDto(Policy p, PolicyAcknowledgement mine, Integer ackCount, Integer headcount) {
        return new PolicyDto(p.getId(), p.getCode(), p.getTitle(), p.getCategory(), p.getSummary(), p.getBody(),
                p.getVersionNo(), p.isRequiresAck(), p.getStatus().name(), p.getEffectiveDate(), p.getPublishedAt(),
                mine != null, mine != null ? mine.getAcknowledgedAt() : null, ackCount, headcount);
    }
}
