package com.hrgenius.payroll.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One employee's pay for one run, with its earning/deduction/employer lines. */
@Getter
@Setter
@Entity
@Table(name = "payslips")
@SQLRestriction("deleted = 0")
public class Payslip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payslip_seq_gen")
    @SequenceGenerator(name = "payslip_seq_gen", sequenceName = "payslip_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private PayrollRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "days_in_period", nullable = false)
    private int daysInPeriod;

    @Column(name = "lop_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal lopDays = BigDecimal.ZERO;

    @Column(name = "paid_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal paidDays;

    @Column(name = "annual_ctc", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualCtc;

    @Column(name = "gross_earnings", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 14, scale = 2)
    private BigDecimal netPay;

    @Column(name = "employer_pf", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerPf = BigDecimal.ZERO;

    @Column(name = "employer_esi", nullable = false, precision = 14, scale = 2)
    private BigDecimal employerEsi = BigDecimal.ZERO;

    @Column(name = "tax_regime", length = 10)
    private String taxRegime;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_masked", length = 40)
    private String accountMasked;

    @Column(name = "bank_ifsc", length = 20)
    private String bankIfsc;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<PayslipLine> lines = new ArrayList<>();

    public void addLine(PayslipLine line) {
        line.setPayslip(this);
        lines.add(line);
    }
}
