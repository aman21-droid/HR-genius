package com.hrgenius.leave.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A leave application. {@code days} is the count of working days (holidays and weekends
 * excluded, half-days counted as 0.5). The approval flow lives in the approval engine;
 * {@code approvalRequestId} links to it.
 */
@Getter
@Setter
@Entity
@Table(name = "leave_requests")
@SQLRestriction("deleted = 0")
public class LeaveRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leave_request_seq_gen")
    @SequenceGenerator(name = "leave_request_seq_gen", sequenceName = "leave_request_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "half_day_start", nullable = false)
    private boolean halfDayStart = false;

    @Column(name = "half_day_end", nullable = false)
    private boolean halfDayEnd = false;

    @Column(name = "days", nullable = false, precision = 6, scale = 2)
    private BigDecimal days;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
