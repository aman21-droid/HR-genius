package com.hrgenius.onboarding.service;

import com.hrgenius.auth.entity.User;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.onboarding.dto.OnboardingDtos.*;
import com.hrgenius.onboarding.entity.*;
import com.hrgenius.onboarding.entity.OnboardingEnums.OwnerRole;
import com.hrgenius.onboarding.entity.OnboardingEnums.PlanStatus;
import com.hrgenius.onboarding.entity.OnboardingEnums.TaskStatus;
import com.hrgenius.onboarding.repository.OnboardingPlanRepository;
import com.hrgenius.onboarding.repository.OnboardingTaskRepository;
import com.hrgenius.onboarding.repository.OnboardingTemplateRepository;
import com.hrgenius.org.entity.MasterEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Onboarding checklists. A plan is generated from a template when a hire is converted (or started
 * manually by HR); HR / MANAGER / EMPLOYEE tasks are assigned to concrete people, team tasks
 * (IT / ADMIN / FINANCE) stay in a shared queue. A plan completes when no task is pending.
 */
@Service
public class OnboardingService {

    public static final String MANAGE_PERMISSION = "ONBOARDING_MANAGE";

    private final OnboardingTemplateRepository templates;
    private final OnboardingPlanRepository plans;
    private final OnboardingTaskRepository tasks;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUser;

    public OnboardingService(OnboardingTemplateRepository templates, OnboardingPlanRepository plans,
                             OnboardingTaskRepository tasks, EmployeeRepository employees,
                             CurrentUserService currentUser) {
        this.templates = templates;
        this.plans = plans;
        this.tasks = tasks;
        this.employees = employees;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public List<TemplateDto> templates() {
        return templates.findAllByOrderByNameAsc().stream().map(OnboardingService::toTemplateDto).toList();
    }

    @Transactional
    public TemplateDto createTemplate(TemplateRequest req) {
        OnboardingTemplate t = new OnboardingTemplate();
        applyTemplate(t, req);
        templates.save(t);
        return toTemplateDto(t);
    }

    @Transactional
    public TemplateDto updateTemplate(Long id, TemplateRequest req) {
        OnboardingTemplate t = findTemplate(id);
        applyTemplate(t, req);
        return toTemplateDto(t);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        OnboardingTemplate t = findTemplate(id);
        if (t.isDefaultTemplate()) {
            throw new BusinessException("Make another template the default before deleting this one");
        }
        t.setDeleted(true);
        t.getTasks().forEach(task -> task.setDeleted(true));
    }

    private void applyTemplate(OnboardingTemplate t, TemplateRequest req) {
        t.setName(req.name().trim());
        t.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        t.setActive(req.active() == null || req.active());
        boolean makeDefault = Boolean.TRUE.equals(req.defaultTemplate());
        if (makeDefault) {
            templates.findAll().stream()
                    .filter(o -> o != t && o.isDefaultTemplate())
                    .forEach(o -> o.setDefaultTemplate(false));
            t.setActive(true);
        }
        // The default only moves by making another template the default, so one always exists.
        t.setDefaultTemplate(makeDefault || t.isDefaultTemplate());
        if (t.isDefaultTemplate() && !t.isActive()) {
            throw new BusinessException("The default template cannot be deactivated");
        }

        // Replace the task list wholesale; orphanRemoval deletes the old rows.
        t.getTasks().clear();
        int order = 10;
        for (TemplateTaskRequest tr : req.tasks()) {
            OnboardingTemplateTask task = new OnboardingTemplateTask();
            task.setTitle(tr.title().trim());
            task.setDescription(tr.description() == null || tr.description().isBlank() ? null : tr.description().trim());
            task.setOwnerRole(tr.ownerRole());
            task.setDueOffsetDays(tr.dueOffsetDays());
            task.setSortOrder(order);
            order += 10;
            t.addTask(task);
        }
    }

    // ------------------------------------------------------------ plans

    /**
     * Generates a plan for an employee. {@code templateId == null} uses the default template;
     * {@code hrEmpId} receives the HR-owned tasks (usually the person starting onboarding).
     */
    @Transactional
    public PlanDto start(Long employeeId, Long templateId, Long applicationId, Long hrEmpId) {
        Employee e = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + employeeId + " not found"));
        if (e.getStatus() == EmployeeStatus.EXITED) {
            throw new BusinessException(e.getFullName() + " has exited; onboarding cannot start");
        }
        if (plans.existsByEmployeeIdAndStatus(employeeId, PlanStatus.IN_PROGRESS)) {
            throw new BusinessException(e.getFullName() + " already has onboarding in progress");
        }
        OnboardingTemplate t = templateId != null ? findTemplate(templateId)
                : templates.findFirstByDefaultTemplateTrueAndActiveTrue()
                        .orElseThrow(() -> new BusinessException("No default onboarding template is configured"));
        if (!t.isActive()) {
            throw new BusinessException("Template '" + t.getName() + "' is inactive");
        }
        Employee hr = hrEmpId == null ? null : employees.findById(hrEmpId).orElse(null);

        OnboardingPlan p = new OnboardingPlan();
        p.setEmployee(e);
        p.setApplicationId(applicationId);
        p.setTemplateId(t.getId());
        p.setStartDate(e.getDateOfJoining());
        p.setStatus(PlanStatus.IN_PROGRESS);
        for (OnboardingTemplateTask tt : t.getTasks()) {
            OnboardingTask task = new OnboardingTask();
            task.setTitle(tt.getTitle());
            task.setDescription(tt.getDescription());
            task.setOwnerRole(tt.getOwnerRole());
            task.setAssignee(switch (tt.getOwnerRole()) {
                case EMPLOYEE -> e;
                case MANAGER -> e.getManager();
                case HR -> hr;
                default -> null;          // IT / ADMIN / FINANCE: shared team queue
            });
            task.setDueDate(e.getDateOfJoining().plusDays(tt.getDueOffsetDays()));
            task.setSortOrder(tt.getSortOrder());
            p.addTask(task);
        }
        plans.save(p);
        return toPlanDto(p);
    }

    @Transactional(readOnly = true)
    public List<PlanSummaryDto> plans() {
        return plans.findAllWithEmployee().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public PlanDto plan(Long id) {
        OnboardingPlan p = findPlan(id);
        requireCanView(p);
        return toPlanDto(p);
    }

    /** The signed-in employee's own onboarding plan, if any. */
    @Transactional(readOnly = true)
    public PlanDto myPlan(Long employeeId) {
        return plans.findFirstByEmployeeIdOrderByIdDesc(employeeId).map(this::toPlanDto).orElse(null);
    }

    /** Pending tasks assigned to the signed-in employee across all plans. */
    @Transactional(readOnly = true)
    public List<TaskDto> myTasks(Long employeeId) {
        return tasks.findAssigned(employeeId, TaskStatus.PENDING).stream().map(this::toTaskDto).toList();
    }

    @Transactional
    public TaskDto setTaskStatus(Long taskId, TaskStatus status) {
        OnboardingTask task = findTask(taskId);
        requireCanWork(task);
        task.setStatus(status);
        if (status == TaskStatus.PENDING) {
            task.setCompletedAt(null);
            task.setCompletedBy(null);
        } else {
            task.setCompletedAt(Instant.now());
            task.setCompletedBy(actorName());
        }
        refreshPlanStatus(task.getPlan());
        return toTaskDto(task);
    }

    @Transactional
    public TaskDto assignTask(Long taskId, TaskAssignRequest req) {
        OnboardingTask task = findTask(taskId);
        if (req.assigneeId() != null) {
            Employee assignee = employees.findById(req.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee " + req.assigneeId() + " not found"));
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }
        if (req.dueDate() != null) {
            task.setDueDate(req.dueDate());
        }
        return toTaskDto(task);
    }

    private void refreshPlanStatus(OnboardingPlan p) {
        boolean anyPending = p.getTasks().stream().anyMatch(t -> t.getStatus() == TaskStatus.PENDING);
        if (!anyPending && p.getStatus() == PlanStatus.IN_PROGRESS) {
            p.setStatus(PlanStatus.COMPLETED);
            p.setCompletedAt(Instant.now());
        } else if (anyPending && p.getStatus() == PlanStatus.COMPLETED) {
            p.setStatus(PlanStatus.IN_PROGRESS);
            p.setCompletedAt(null);
        }
    }

    // ------------------------------------------------------------ access

    private boolean isManager() {
        return currentUser.hasAuthority(MANAGE_PERMISSION);
    }

    /** HR, the new hire, their manager, or anyone holding a task on the plan may view it. */
    private void requireCanView(OnboardingPlan p) {
        if (isManager()) {
            return;
        }
        Long me = currentUser.employeeId().orElse(null);
        boolean involved = me != null && (me.equals(p.getEmployee().getId())
                || (p.getEmployee().getManager() != null && me.equals(p.getEmployee().getManager().getId()))
                || p.getTasks().stream().anyMatch(t -> t.getAssignee() != null && me.equals(t.getAssignee().getId())));
        if (!involved) {
            throw new AccessDeniedException("Not allowed to view this onboarding plan");
        }
    }

    /** HR may work any task; everyone else only the tasks assigned to them. */
    private void requireCanWork(OnboardingTask task) {
        if (isManager()) {
            return;
        }
        Long me = currentUser.employeeId().orElse(null);
        if (me == null || task.getAssignee() == null || !me.equals(task.getAssignee().getId())) {
            throw new AccessDeniedException("This task is not assigned to you");
        }
    }

    // ------------------------------------------------------------ mapping

    private OnboardingTemplate findTemplate(Long id) {
        return templates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Onboarding template " + id + " not found"));
    }

    private OnboardingPlan findPlan(Long id) {
        return plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Onboarding plan " + id + " not found"));
    }

    private OnboardingTask findTask(Long id) {
        return tasks.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Onboarding task " + id + " not found"));
    }

    private String actorName() {
        return currentUser.user().map(User::getFullName).filter(n -> n != null && !n.isBlank())
                .orElseGet(currentUser::email);
    }

    private static TemplateDto toTemplateDto(OnboardingTemplate t) {
        return new TemplateDto(t.getId(), t.getName(), t.getDescription(), t.isDefaultTemplate(), t.isActive(),
                t.getTasks().stream().map(tt -> new TemplateTaskDto(tt.getId(), tt.getTitle(), tt.getDescription(),
                        tt.getOwnerRole().name(), tt.getDueOffsetDays(), tt.getSortOrder())).toList());
    }

    private PlanSummaryDto toSummary(OnboardingPlan p) {
        Employee e = p.getEmployee();
        LocalDate today = LocalDate.now();
        int done = (int) p.getTasks().stream().filter(t -> t.getStatus() != TaskStatus.PENDING).count();
        int overdue = (int) p.getTasks().stream().filter(t -> isOverdue(t, today)).count();
        return new PlanSummaryDto(p.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(),
                nameOf(e.getDesignation()), nameOf(e.getDepartment()), p.getStartDate(), p.getStatus().name(),
                p.getTasks().size(), done, overdue, p.getCompletedAt());
    }

    private PlanDto toPlanDto(OnboardingPlan p) {
        Employee e = p.getEmployee();
        return new PlanDto(p.getId(), e.getId(), e.getEmployeeCode(), e.getFullName(), nameOf(e.getDesignation()),
                nameOf(e.getDepartment()), e.getManager() != null ? e.getManager().getFullName() : null,
                p.getStartDate(), p.getStatus().name(), p.getApplicationId(), p.getCompletedAt(),
                p.getTasks().stream().map(this::toTaskDto).toList());
    }

    private TaskDto toTaskDto(OnboardingTask t) {
        Employee a = t.getAssignee();
        Employee hire = t.getPlan().getEmployee();
        return new TaskDto(t.getId(), t.getPlan().getId(), t.getTitle(), t.getDescription(), t.getOwnerRole().name(),
                a != null ? a.getId() : null, a != null ? a.getFullName() : null, t.getDueDate(), t.getStatus().name(),
                t.getCompletedAt(), t.getCompletedBy(), isOverdue(t, LocalDate.now()),
                hire.getId(), hire.getFullName());
    }

    private static boolean isOverdue(OnboardingTask t, LocalDate today) {
        return t.getStatus() == TaskStatus.PENDING && t.getDueDate() != null && t.getDueDate().isBefore(today);
    }

    private static String nameOf(MasterEntity m) {
        return m == null ? null : m.getName();
    }

    /** Owner roles whose tasks form a shared team queue rather than being assigned to one person. */
    public static boolean isTeamQueue(OwnerRole role) {
        return role == OwnerRole.IT || role == OwnerRole.ADMIN || role == OwnerRole.FINANCE;
    }
}
