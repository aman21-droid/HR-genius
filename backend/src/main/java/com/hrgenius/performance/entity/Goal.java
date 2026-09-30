package com.hrgenius.performance.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.performance.entity.PerformanceEnums.GoalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/** A weighted goal inside a review; self and manager ratings are stored per goal. */
@Getter
@Setter
@Entity
@Table(name = "goals")
@SQLRestriction("deleted = 0")
public class Goal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "goal_seq_gen")
    @SequenceGenerator(name = "goal_seq_gen", sequenceName = "goal_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoalStatus status = GoalStatus.NOT_STARTED;

    @Column(name = "self_rating")
    private Integer selfRating;

    @Column(name = "self_comment", length = 1000)
    private String selfComment;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_comment", length = 1000)
    private String managerComment;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
