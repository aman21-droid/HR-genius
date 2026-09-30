package com.hrgenius.approval.entity;

import com.hrgenius.approval.entity.ApprovalEnums.ApprovalStatus;
import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One instance of an approval flow. {@code subjectType}/{@code subjectId} point back at the
 * domain row that owns the flow (e.g. LEAVE -> leave_requests.id) so an
 * {@code ApprovalOutcomeHandler} can act when the flow resolves. Approvers are referenced by
 * employee id rather than a JPA relation to keep the engine decoupled from every module.
 */
@Getter
@Setter
@Entity
@Table(name = "approval_requests")
@SQLRestriction("deleted = 0")
public class ApprovalRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "approval_request_seq_gen")
    @SequenceGenerator(name = "approval_request_seq_gen", sequenceName = "approval_request_seq", allocationSize = 1)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 30, updatable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "requester_emp_id", nullable = false, updatable = false)
    private Long requesterEmpId;

    @Column(name = "title", length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** 1-based index of the step awaiting a decision. */
    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps = 1;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    public void addStep(ApprovalStep step) {
        step.setRequest(this);
        steps.add(step);
    }
}
