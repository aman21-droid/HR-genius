package com.hrgenius.onboarding.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/** A reusable onboarding checklist. At most one template is the default for new hires. */
@Getter
@Setter
@Entity
@Table(name = "onboarding_templates")
@SQLRestriction("deleted = 0")
public class OnboardingTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "onboarding_template_seq_gen")
    @SequenceGenerator(name = "onboarding_template_seq_gen", sequenceName = "onboarding_template_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<OnboardingTemplateTask> tasks = new ArrayList<>();

    public void addTask(OnboardingTemplateTask task) {
        task.setTemplate(this);
        tasks.add(task);
    }
}
