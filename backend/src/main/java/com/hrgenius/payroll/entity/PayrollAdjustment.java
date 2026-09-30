package com.hrgenius.payroll.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/** A one-off earning or deduction for an employee in a specific run. */
@Getter
@Setter
@Entity
@Table(name = "payroll_adjustments")
@SQLRestriction("deleted = 0")
public class PayrollAdjustment extends BaseEntity {

    public enum AdjustmentType { EARNING, DEDUCTION }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payroll_adjustment_seq_gen")
    @SequenceGenerator(name = "payroll_adjustment_seq_gen", sequenceName = "payroll_adjustment_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private PayrollRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 10)
    private AdjustmentType adjustmentType;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;
}
