package com.hrgenius.performance.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.performance.entity.PerformanceEnums.CycleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

/** A review period (e.g. "H2 2026"). Launching opens a review for each eligible employee. */
@Getter
@Setter
@Entity
@Table(name = "review_cycles")
@SQLRestriction("deleted = 0")
public class ReviewCycle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "review_cycle_seq_gen")
    @SequenceGenerator(name = "review_cycle_seq_gen", sequenceName = "review_cycle_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "self_review_due")
    private LocalDate selfReviewDue;

    @Column(name = "manager_review_due")
    private LocalDate managerReviewDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CycleStatus status = CycleStatus.DRAFT;

    @Column(name = "launched_at")
    private Instant launchedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
