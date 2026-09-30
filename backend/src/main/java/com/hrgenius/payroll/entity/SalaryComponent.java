package com.hrgenius.payroll.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.payroll.calc.PayrollCalculator.CalcType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/** One earnings line of the salary structure (see {@link CalcType} for how it is computed). */
@Getter
@Setter
@Entity
@Table(name = "salary_components")
@SQLRestriction("deleted = 0")
public class SalaryComponent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "salary_component_seq_gen")
    @SequenceGenerator(name = "salary_component_seq_gen", sequenceName = "salary_component_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "calc_type", nullable = false, length = 20)
    private CalcType calcType;

    @Column(name = "calc_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal calcValue = BigDecimal.ZERO;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
