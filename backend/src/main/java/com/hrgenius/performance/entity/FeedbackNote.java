package com.hrgenius.performance.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.performance.entity.PerformanceEnums.FeedbackKind;
import com.hrgenius.performance.entity.PerformanceEnums.Visibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Praise or constructive feedback from one employee to another. */
@Getter
@Setter
@Entity
@Table(name = "feedback_notes")
@SQLRestriction("deleted = 0")
public class FeedbackNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feedback_note_seq_gen")
    @SequenceGenerator(name = "feedback_note_seq_gen", sequenceName = "feedback_note_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_emp_id", nullable = false, updatable = false)
    private Employee author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_emp_id", nullable = false, updatable = false)
    private Employee recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private FeedbackKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private Visibility visibility;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;
}
