package com.hrgenius.leave.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.leave.entity.LeaveEnums.AccrualMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/** Configurable leave type: entitlement, accrual policy, carry-forward and flags. */
@Getter
@Setter
@Entity
@Table(name = "leave_types")
@SQLRestriction("deleted = 0")
public class LeaveType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leave_type_seq_gen")
    @SequenceGenerator(name = "leave_type_seq_gen", sequenceName = "leave_type_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "annual_entitlement", nullable = false, precision = 6, scale = 2)
    private BigDecimal annualEntitlement = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_method", nullable = false, length = 20)
    private AccrualMethod accrualMethod = AccrualMethod.ANNUAL;

    /** Units granted per accrual period (per month for MONTHLY, per year for ANNUAL). */
    @Column(name = "accrual_rate", nullable = false, precision = 6, scale = 2)
    private BigDecimal accrualRate = BigDecimal.ZERO;

    /** Max units that roll into next year at year-end. */
    @Column(name = "carry_forward_cap", nullable = false, precision = 6, scale = 2)
    private BigDecimal carryForwardCap = BigDecimal.ZERO;

    /** Optional ceiling on total balance; null = uncapped. */
    @Column(name = "max_balance", precision = 6, scale = 2)
    private BigDecimal maxBalance;

    @Column(name = "allow_half_day", nullable = false)
    private boolean allowHalfDay = true;

    @Column(name = "encashable", nullable = false)
    private boolean encashable = false;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
