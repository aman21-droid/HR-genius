package com.hrgenius.onboarding.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.onboarding.entity.OnboardingEnums.PlanStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A new hire's onboarding checklist, generated from a template. */
@Getter
@Setter
@Entity
@Table(name = "onboarding_plans")
@SQLRestriction("deleted = 0")
public class OnboardingPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "onboarding_plan_seq_gen")
    @SequenceGenerator(name = "onboarding_plan_seq_gen", sequenceName = "onboarding_plan_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status = PlanStatus.IN_PROGRESS;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<OnboardingTask> tasks = new ArrayList<>();

    public void addTask(OnboardingTask task) {
        task.setPlan(this);
        tasks.add(task);
    }
}
