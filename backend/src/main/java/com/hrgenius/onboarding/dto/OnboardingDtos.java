package com.hrgenius.onboarding.dto;

import com.hrgenius.onboarding.entity.OnboardingEnums.OwnerRole;
import com.hrgenius.onboarding.entity.OnboardingEnums.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for the onboarding API. */
public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    // ------------------------------------------------------------ templates

    public record TemplateDto(Long id, String name, String description, boolean defaultTemplate, boolean active,
                              List<TemplateTaskDto> tasks) {
    }

    public record TemplateTaskDto(Long id, String title, String description, String ownerRole,
                                  int dueOffsetDays, int sortOrder) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            Boolean defaultTemplate,
            Boolean active,
            @NotEmpty @Size(max = 60) List<@Valid TemplateTaskRequest> tasks) {
    }

    public record TemplateTaskRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 1000) String description,
            @NotNull OwnerRole ownerRole,
            @NotNull @Min(-60) @Max(365) Integer dueOffsetDays) {
    }

    // ------------------------------------------------------------ plans & tasks

    public record PlanSummaryDto(Long id, Long employeeId, String employeeCode, String employeeName,
                                 String designation, String department, LocalDate startDate, String status,
                                 int totalTasks, int doneTasks, int overdueTasks, Instant completedAt) {
    }

    public record PlanDto(Long id, Long employeeId, String employeeCode, String employeeName, String designation,
                          String department, String managerName, LocalDate startDate, String status,
                          Long applicationId, Instant completedAt, List<TaskDto> tasks) {
    }

    public record TaskDto(Long id, Long planId, String title, String description, String ownerRole,
                          Long assigneeId, String assigneeName, LocalDate dueDate, String status,
                          Instant completedAt, String completedBy, boolean overdue,
                          /* for "my tasks": whose onboarding this is */
                          Long newHireId, String newHireName) {
    }

    public record StartPlanRequest(@NotNull Long employeeId, Long templateId) {
    }

    public record TaskStatusRequest(@NotNull TaskStatus status) {
    }

    public record TaskAssignRequest(Long assigneeId, LocalDate dueDate) {
    }
}
