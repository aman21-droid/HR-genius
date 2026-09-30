package com.hrgenius.leave.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * An employee's balance for one leave type in one calendar year.
 * Available = opening + accrued + adjustment - used - pending.
 */
@Getter
@Setter
@Entity
@Table(name = "leave_balances")
public class LeaveBalance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leave_balance_seq_gen")
    @SequenceGenerator(name = "leave_balance_seq_gen", sequenceName = "leave_balance_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year_no", nullable = false)
    private int year;

    @Column(name = "opening", nullable = false, precision = 6, scale = 2)
    private BigDecimal opening = BigDecimal.ZERO;

    @Column(name = "accrued", nullable = false, precision = 6, scale = 2)
    private BigDecimal accrued = BigDecimal.ZERO;

    @Column(name = "used", nullable = false, precision = 6, scale = 2)
    private BigDecimal used = BigDecimal.ZERO;

    /** Reserved by requests that are submitted but not yet approved. */
    @Column(name = "pending", nullable = false, precision = 6, scale = 2)
    private BigDecimal pending = BigDecimal.ZERO;

    /** Manual HR correction (+/-). */
    @Column(name = "adjustment", nullable = false, precision = 6, scale = 2)
    private BigDecimal adjustment = BigDecimal.ZERO;

    /** Balance actually available to book against. */
    @Transient
    public BigDecimal getAvailable() {
        return opening.add(accrued).add(adjustment).subtract(used).subtract(pending);
    }
}
