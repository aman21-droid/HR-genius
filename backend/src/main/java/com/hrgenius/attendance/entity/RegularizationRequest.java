package com.hrgenius.attendance.entity;

import com.hrgenius.attendance.entity.AttendanceEnums.RegularizationStatus;
import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A request to correct a day's punches (missed check-in/out, wrong time). Routed through the
 * approval engine; on approval the target {@link AttendanceDay} is updated and recomputed.
 */
@Getter
@Setter
@Entity
@Table(name = "regularization_requests")
@SQLRestriction("deleted = 0")
public class RegularizationRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "regularization_seq_gen")
    @SequenceGenerator(name = "regularization_seq_gen", sequenceName = "regularization_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "requested_check_in")
    private Instant requestedCheckIn;

    @Column(name = "requested_check_out")
    private Instant requestedCheckOut;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegularizationStatus status = RegularizationStatus.PENDING;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
