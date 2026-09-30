package com.hrgenius.compliance.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/** An employee's confirmation that they have read a specific version of a policy. */
@Getter
@Setter
@Entity
@Table(name = "policy_acknowledgements")
@SQLRestriction("deleted = 0")
public class PolicyAcknowledgement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "policy_ack_seq_gen")
    @SequenceGenerator(name = "policy_ack_seq_gen", sequenceName = "policy_ack_seq", allocationSize = 1)
    private Long id;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private Instant acknowledgedAt;
}
