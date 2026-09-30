package com.hrgenius.payroll.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A month's payroll. Totals are denormalised from its payslips at calculation time. */
@Getter
@Setter
@Entity
@Table(name = "payroll_runs")
@SQLRestriction("deleted = 0")
public class PayrollRun extends BaseEntity {

    /** DRAFT -> CALCULATED -> PENDING_APPROVAL -> APPROVED -> PAID. */
    public enum RunStatus { DRAFT, CALCULATED, PENDING_APPROVAL, APPROVED, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payroll_run_seq_gen")
    @SequenceGenerator(name = "payroll_run_seq_gen", sequenceName = "payroll_run_seq", allocationSize = 1)
    private Long id;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RunStatus status = RunStatus.DRAFT;

    @Column(name = "employee_count", nullable = false)
    private int employeeCount;

    @Column(name = "total_gross", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_employer_cost", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalEmployerCost = BigDecimal.ZERO;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference", length = 80)
    private String paymentReference;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Payslips are visible to employees once the run is approved. */
    public boolean isPublished() {
        return status == RunStatus.APPROVED || status == RunStatus.PAID;
    }
}
