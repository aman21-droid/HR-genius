package com.hrgenius.employee.service;

import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.employee.repository.EmployeeRepository.ManagerLink;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Central answer to "may the caller see / change this employee's data?".
 *
 * <ul>
 *   <li><b>Directory</b> fields (name, designation, work contact, manager) are visible to every
 *       signed-in user, like a company phone book.</li>
 *   <li><b>Full profile</b> (personal details, documents, assets, timeline, contacts): full-access
 *       HR roles for everyone, managers for their whole reporting tree, and each person for self.</li>
 *   <li><b>Sensitive</b> (compensation, bank/statutory IDs): EMPLOYEE_SENSITIVE_READ, or self.</li>
 * </ul>
 */
@Service
public class EmployeeAccessService {

    /** Roles that see every employee's full profile. Recruiters deliberately excluded. */
    private static final List<String> FULL_ACCESS_ROLES = List.of("SUPER_ADMIN", "HR_ADMIN", "HR_MANAGER", "PAYROLL_ADMIN");

    private final CurrentUserService currentUser;
    private final EmployeeRepository employeeRepository;

    public EmployeeAccessService(CurrentUserService currentUser, EmployeeRepository employeeRepository) {
        this.currentUser = currentUser;
        this.employeeRepository = employeeRepository;
    }

    public boolean hasFullAccess() {
        return FULL_ACCESS_ROLES.stream().anyMatch(currentUser::hasRole);
    }

    public Optional<Long> selfId() {
        return currentUser.employeeId();
    }

    public boolean isSelf(Long employeeId) {
        return selfId().map(id -> id.equals(employeeId)).orElse(false);
    }

    public boolean canViewFullProfile(Long employeeId) {
        if (hasFullAccess() || isSelf(employeeId)) {
            return true;
        }
        return currentUser.hasRole("MANAGER") && myReportingTree().contains(employeeId);
    }

    public boolean canViewSensitive(Long employeeId) {
        return currentUser.hasAuthority("EMPLOYEE_SENSITIVE_READ") || isSelf(employeeId);
    }

    public boolean canEdit() {
        return currentUser.hasAuthority("EMPLOYEE_WRITE");
    }

    public void requireFullProfile(Long employeeId) {
        if (!canViewFullProfile(employeeId)) {
            throw new AccessDeniedException("Not allowed to view this employee's profile");
        }
    }

    public void requireSensitive(Long employeeId) {
        if (!canViewSensitive(employeeId)) {
            throw new AccessDeniedException("Not allowed to view sensitive data for this employee");
        }
    }

    /** Self-service or HR: e.g. emergency contacts and document uploads. */
    public void requireSelfOrEditor(Long employeeId) {
        if (!canEdit() && !isSelf(employeeId)) {
            throw new AccessDeniedException("Only HR or the employee can change this");
        }
    }

    /** All current employees below the caller in the hierarchy (excluding the caller). */
    public Set<Long> myReportingTree() {
        return selfId().map(this::reportingTree).orElse(Set.of());
    }

    public Set<Long> reportingTree(Long rootEmployeeId) {
        return collectDescendants(rootEmployeeId, employeeRepository.findManagerLinks(EmployeeStatus.EXITED));
    }

    /**
     * Breadth-first walk from {@code rootId} over (id, managerId) pairs. Visited-set guarded,
     * so corrupt data containing a cycle cannot loop forever.
     */
    static Set<Long> collectDescendants(Long rootId, List<? extends ManagerLink> links) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (ManagerLink link : links) {
            if (link.getManagerId() != null) {
                children.computeIfAbsent(link.getManagerId(), k -> new ArrayList<>()).add(link.getId());
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(children.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!id.equals(rootId) && result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }
}
