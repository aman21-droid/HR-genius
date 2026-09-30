package com.hrgenius.onboarding.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.onboarding.entity.OnboardingEnums.OwnerRole;
import com.hrgenius.onboarding.entity.OnboardingEnums.TaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

/** One checklist item in a plan. {@code assignee} is null for team-queue tasks (IT/ADMIN/FINANCE). */
@Getter
@Setter
@Entity
@Table(name = "onboarding_tasks")
@SQLRestriction("deleted = 0")
public class OnboardingTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "onboarding_task_seq_gen")
    @SequenceGenerator(name = "onboarding_task_seq_gen", sequenceName = "onboarding_task_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private OnboardingPlan plan;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", nullable = false, length = 20)
    private OwnerRole ownerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_emp_id")
    private Employee assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 160)
    private String completedBy;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
