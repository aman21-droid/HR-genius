package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** A candidate in one requisition's pipeline. {@code employeeId} is set once the hire is converted. */
@Getter
@Setter
@Entity
@Table(name = "job_applications")
@SQLRestriction("deleted = 0")
public class JobApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "application_seq_gen")
    @SequenceGenerator(name = "application_seq_gen", sequenceName = "application_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false, updatable = false)
    private JobRequisition requisition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private ApplicationStage stage = ApplicationStage.APPLIED;

    @Column(name = "stage_changed_at", nullable = false)
    private Instant stageChangedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CandidateSource source = CandidateSource.DIRECT;

    @Column(name = "cover_note", length = 2000)
    private String coverNote;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "employee_id")
    private Long employeeId;
}
