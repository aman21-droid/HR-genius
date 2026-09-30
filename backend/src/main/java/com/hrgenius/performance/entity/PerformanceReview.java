package com.hrgenius.performance.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.performance.entity.PerformanceEnums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One employee's review in a cycle, with their goals. */
@Getter
@Setter
@Entity
@Table(name = "performance_reviews")
@SQLRestriction("deleted = 0")
public class PerformanceReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "performance_review_seq_gen")
    @SequenceGenerator(name = "performance_review_seq_gen", sequenceName = "performance_review_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, updatable = false)
    private ReviewCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_emp_id", nullable = false)
    private Employee reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.NOT_STARTED;

    @Column(name = "self_rating")
    private Integer selfRating;

    @Column(name = "self_comments", length = 4000)
    private String selfComments;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_comments", length = 4000)
    private String managerComments;

    @Column(name = "final_score", precision = 4, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "ack_comment", length = 1000)
    private String ackComment;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<Goal> goals = new ArrayList<>();

    public void addGoal(Goal g) {
        g.setReview(this);
        goals.add(g);
    }
}
