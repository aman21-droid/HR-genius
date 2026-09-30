package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.recruitment.entity.RecruitmentEnums.Recommendation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** A panelist's scorecard for an interview. {@code submittedAt == null} means feedback is still owed. */
@Getter
@Setter
@Entity
@Table(name = "interview_feedback")
@SQLRestriction("deleted = 0")
public class InterviewFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interview_feedback_seq_gen")
    @SequenceGenerator(name = "interview_feedback_seq_gen", sequenceName = "interview_feedback_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false, updatable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interviewer_emp_id", nullable = false, updatable = false)
    private Employee interviewer;

    @Column(name = "rating")
    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", length = 20)
    private Recommendation recommendation;

    @Column(name = "strengths", length = 2000)
    private String strengths;

    @Column(name = "concerns", length = 2000)
    private String concerns;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    public boolean isSubmitted() {
        return submittedAt != null;
    }
}
