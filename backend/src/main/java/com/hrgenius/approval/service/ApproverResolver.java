package com.hrgenius.approval.service;

import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.UserRepository;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the approver chain for a request. Default rule: reporting manager first, then an HR
 * approver (any active account holding LEAVE_APPROVE). Duplicates and self-approval are removed;
 * an empty chain means nobody can approve, so the caller may auto-approve.
 */
@Service
public class ApproverResolver {

    private static final String HR_PERMISSION = "LEAVE_APPROVE";

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

    /** An HR approver's employee id that is neither the requester nor the already-chosen manager. */
    private Long firstHrApprover(Long requesterEmpId, Long managerId) {
        for (User u : users.findApproversByPermission(HR_PERMISSION)) {
            Long empId = u.getEmployeeId();
            if (empId != null && !empId.equals(requesterEmpId) && !empId.equals(managerId)) {
                return empId;
            }
        }
        return null;
    }
}
