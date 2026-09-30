package com.hrgenius.onboarding.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.onboarding.entity.OnboardingEnums.OwnerRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** A checklist item in a template; {@code dueOffsetDays} is relative to the joining date. */
@Getter
@Setter
@Entity
@Table(name = "onboarding_template_tasks")
@SQLRestriction("deleted = 0")
public class OnboardingTemplateTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "onboarding_template_task_seq_gen")
    @SequenceGenerator(name = "onboarding_template_task_seq_gen", sequenceName = "onboarding_template_task_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private OnboardingTemplate template;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", nullable = false, length = 20)
    private OwnerRole ownerRole;

    @Column(name = "due_offset_days", nullable = false)
    private int dueOffsetDays = 0;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
