package com.hrgenius.approval.controller;

import com.hrgenius.approval.dto.ApprovalDtos.*;
import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.entity.ApprovalStep;
import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The approver's inbox and the requester's own trail, over the generic approval engine.
 * All endpoints act as the caller's linked employee, so no employee id is taken from the client.
 */
@Tag(name = "Approvals")
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvals;
    private final CurrentUserService currentUser;
    private final EmployeeRepository employees;

    public ApprovalController(ApprovalService approvals, CurrentUserService currentUser,
                              EmployeeRepository employees) {
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.employees = employees;
    }

    @Operation(summary = "Items awaiting my decision")
    @GetMapping("/inbox")
    public List<InboxItem> inbox() {
        Map<Long, String> names = new HashMap<>();
        return approvals.inbox(me()).stream().map(s -> toInboxItem(s, names)).toList();
    }

    @Operation(summary = "Count of items awaiting my decision (for the nav badge)")
    @GetMapping("/inbox/count")
    public Map<String, Long> inboxCount() {
        return Map.of("count", approvals.inboxCount(me()));
    }

    @Operation(summary = "Requests I have raised, with their step trail")
    @GetMapping("/mine")
    public List<MyRequestItem> mine() {
        Map<Long, String> names = new HashMap<>();
        return approvals.myRequests(me()).stream().map(r -> toMyRequest(r, names)).toList();
    }

    @Operation(summary = "Approve or reject the active step of a request")
    @PostMapping("/steps/{stepId}/decide")
    public MyRequestItem decide(@PathVariable Long stepId, @Valid @RequestBody DecisionRequest req) {
        ApprovalRequest updated = approvals.decide(stepId, me(), req.approve(), req.comment());
        return toMyRequest(updated, new HashMap<>());
    }

    // ------------------------------------------------------------------ helpers

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }

    private String nameOf(Long empId, Map<Long, String> cache) {
        if (empId == null) {
            return null;
        }
        return cache.computeIfAbsent(empId, id -> employees.findById(id)
                .map(Employee::getFullName).orElse("Employee #" + id));
    }

    private InboxItem toInboxItem(ApprovalStep s, Map<Long, String> names) {
        ApprovalRequest r = s.getRequest();
        return new InboxItem(s.getId(), r.getId(), r.getSubjectType(), r.getSubjectId(), r.getTitle(),
                r.getRequesterEmpId(), nameOf(r.getRequesterEmpId(), names), s.getRoleHint(),
                s.getStepNo(), r.getTotalSteps(), r.getCreatedAt());
    }

    private MyRequestItem toMyRequest(ApprovalRequest r, Map<Long, String> names) {
        List<StepView> steps = r.getSteps().stream()
                .map(s -> new StepView(s.getStepNo(), s.getApproverEmpId(), nameOf(s.getApproverEmpId(), names),
                        s.getRoleHint(), s.getStatus().name(), s.getComment(), s.getDecidedAt()))
                .toList();
        return new MyRequestItem(r.getId(), r.getSubjectType(), r.getSubjectId(), r.getTitle(),
                r.getStatus().name(), r.getCurrentStep(), r.getTotalSteps(), steps,
                r.getCreatedAt(), r.getResolvedAt());
    }
}
