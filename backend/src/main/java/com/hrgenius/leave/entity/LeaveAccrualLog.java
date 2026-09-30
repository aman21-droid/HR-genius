package com.hrgenius.leave.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Idempotency ledger for the accrual job: one row per employee/type/period actually posted,
 * so re-running the job never double-credits. {@code period} is YYYY-MM (MONTHLY) or YYYY (ANNUAL).
 */
@Getter
@Setter
@Entity
@Table(name = "leave_accrual_log")
public class LeaveAccrualLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leave_accrual_seq_gen")
    @SequenceGenerator(name = "leave_accrual_seq_gen", sequenceName = "leave_accrual_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "amount", nullable = false, precision = 6, scale = 2)
    private BigDecimal amount;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;
}
