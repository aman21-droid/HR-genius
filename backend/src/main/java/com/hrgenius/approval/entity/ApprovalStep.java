package com.hrgenius.approval.entity;

import com.hrgenius.approval.entity.ApprovalEnums.StepStatus;
import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** One ordered approval step. The approver is resolved to a concrete employee at creation time. */
@Getter
@Setter
@Entity
@Table(name = "approval_steps")
@SQLRestriction("deleted = 0")
public class ApprovalStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "approval_step_seq_gen")
    @SequenceGenerator(name = "approval_step_seq_gen", sequenceName = "approval_step_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "approver_emp_id", nullable = false)
    private Long approverEmpId;

    /** Informational label for how this approver was chosen (MANAGER, HR, ...). */
    @Column(name = "role_hint", length = 40)
    private String roleHint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StepStatus status = StepStatus.PENDING;

    @Column(name = "decision_comment", length = 500)
    private String comment;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
