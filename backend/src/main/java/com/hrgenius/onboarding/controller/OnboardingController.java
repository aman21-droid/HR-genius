package com.hrgenius.onboarding.controller;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.onboarding.dto.OnboardingDtos.*;
import com.hrgenius.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Onboarding API. Templates and the plan list need ONBOARDING_MANAGE; "my" endpoints and task
 * completion are open to any signed-in employee, with per-plan/per-task checks in the service.
 */
@Tag(name = "Onboarding")
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private static final String MANAGE = "hasAuthority('ONBOARDING_MANAGE')";

    private final OnboardingService onboarding;
    private final CurrentUserService currentUser;

    public OnboardingController(OnboardingService onboarding, CurrentUserService currentUser) {
        this.onboarding = onboarding;
        this.currentUser = currentUser;
    }

    // ---- templates ----

    @Operation(summary = "Onboarding templates")
    @GetMapping("/templates")
    @PreAuthorize(MANAGE)
    public List<TemplateDto> templates() {
        return onboarding.templates();
    }

    @Operation(summary = "Create a template")
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public TemplateDto createTemplate(@Valid @RequestBody TemplateRequest req) {
        return onboarding.createTemplate(req);
    }

    @Operation(summary = "Replace a template (name, flags and task list)")
    @PutMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public TemplateDto updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateRequest req) {
        return onboarding.updateTemplate(id, req);
    }

    @Operation(summary = "Delete a template")
    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    public void deleteTemplate(@PathVariable Long id) {
        onboarding.deleteTemplate(id);
    }

    // ---- plans ----

    @Operation(summary = "All onboarding plans with progress")
    @GetMapping("/plans")
    @PreAuthorize(MANAGE)
    public List<PlanSummaryDto> plans() {
        return onboarding.plans();
    }

    @Operation(summary = "Start onboarding for an existing employee")
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public PlanDto start(@Valid @RequestBody StartPlanRequest req) {
        return onboarding.start(req.employeeId(), req.templateId(), null, currentUser.employeeId().orElse(null));
    }

    @Operation(summary = "One plan with its tasks (HR, the new hire, their manager, or a task assignee)")
    @GetMapping("/plans/{id}")
    public PlanDto plan(@PathVariable Long id) {
        return onboarding.plan(id);
    }

    @Operation(summary = "My own onboarding plan (204 if none)")
    @GetMapping("/me/plan")
    public ResponseEntity<PlanDto> myPlan() {
        PlanDto plan = onboarding.myPlan(me());
        return plan == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(plan);
    }

    @Operation(summary = "Pending onboarding tasks assigned to me")
    @GetMapping("/me/tasks")
    public List<TaskDto> myTasks() {
        return onboarding.myTasks(me());
    }

    // ---- tasks ----

    @Operation(summary = "Mark a task done, skipped or pending again")
    @PostMapping("/tasks/{id}/status")
    public TaskDto setTaskStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest req) {
        return onboarding.setTaskStatus(id, req.status());
    }

    @Operation(summary = "Reassign a task or move its due date")
    @PatchMapping("/tasks/{id}")
    @PreAuthorize(MANAGE)
    public TaskDto assignTask(@PathVariable Long id, @RequestBody TaskAssignRequest req) {
        return onboarding.assignTask(id, req);
    }

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
