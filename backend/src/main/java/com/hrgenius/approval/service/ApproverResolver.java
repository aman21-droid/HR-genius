package com.hrgenius.approval.service;

import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.UserRepository;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the approver chain for a request. Default rule: reporting manager first, then an HR
 * approver (any active account holding LEAVE_APPROVE). Duplicates and self-approval are removed;
 * an empty chain means nobody can approve, so the caller may auto-approve.
 */
@Service
public class ApproverResolver {

    private static final String HR_PERMISSION = "LEAVE_APPROVE";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final EmployeeRepository employees;
    private final UserRepository users;

    public ApproverResolver(EmployeeRepository employees, UserRepository users) {
        this.employees = employees;
        this.users = users;
    }

    /** Manager -> HR chain for the given requester, de-duplicated and excluding the requester. */
    public List<Approver> forEmployee(Long requesterEmpId) {
        List<Approver> chain = new ArrayList<>();

        Employee requester = employees.findById(requesterEmpId).orElse(null);
        Long managerId = requester != null && requester.getManager() != null ? requester.getManager().getId() : null;
        if (managerId != null && !managerId.equals(requesterEmpId)) {
            chain.add(new Approver(managerId, "MANAGER"));
        }

        Long hrEmpId = firstHrApprover(requesterEmpId, managerId);
        if (hrEmpId != null) {
            chain.add(new Approver(hrEmpId, "HR"));
        }
        return chain;
    }

    /**
     * Generic two-step chain: a named first approver (e.g. department head or hiring manager)
     * followed by an account holding {@code hrPermission}. Either step is dropped when it would be
     * the requester or a duplicate; an empty result lets the caller auto-approve.
     */
    public List<Approver> chainOf(Long requesterEmpId, Long firstApproverEmpId, String firstRoleHint,
                                  String hrPermission) {
        List<Approver> chain = new ArrayList<>();
        Long first = firstApproverEmpId != null && !firstApproverEmpId.equals(requesterEmpId)
                ? firstApproverEmpId : null;
        if (first != null) {
            chain.add(new Approver(first, firstRoleHint));
        }
        Long hrEmpId = firstApproverWith(hrPermission, requesterEmpId, first);
        if (hrEmpId != null) {
            chain.add(new Approver(hrEmpId, "HR"));
        }
        return chain;
    }

    /** An HR approver's employee id that is neither the requester nor the already-chosen manager. */
    private Long firstHrApprover(Long requesterEmpId, Long managerId) {
        return firstApproverWith(HR_PERMISSION, requesterEmpId, managerId);
    }

    /**
     * First eligible holder of {@code permission}. Candidates are ordered so a dedicated HR account
     * is preferred over a super-admin (who holds every permission), then by id, making the choice
     * deterministic.
     */
    private Long firstApproverWith(String permission, Long requesterEmpId, Long alreadyChosen) {
        List<User> holders = new ArrayList<>(users.findApproversByPermission(permission));
        holders.sort(Comparator
                .comparing((User u) -> u.getRoles().stream().anyMatch(r -> SUPER_ADMIN.equals(r.getCode())))
                .thenComparing(User::getId));
        for (User u : holders) {
            Long empId = u.getEmployeeId();
            if (empId != null && !empId.equals(requesterEmpId) && !empId.equals(alreadyChosen)) {
                return empId;
            }
        }
        return null;
    }
}
