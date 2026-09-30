package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.EmployeeEnums.*;
import com.hrgenius.org.entity.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Core employee record: personal details, job placement, and compensation headline.
 * Bank/statutory identifiers live in {@link EmployeeStatutory} so list queries never
 * load (or decrypt) sensitive data.
 */
@Getter
@Setter
@Entity
@Table(name = "employees")
@SQLRestriction("deleted = 0")
public class Employee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_seq_gen")
    @SequenceGenerator(name = "employee_seq_gen", sequenceName = "employee_seq", allocationSize = 1)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20, updatable = false)
    private String employeeCode;

    // ---- Personal ----
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "middle_name", length = 80)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "work_email", nullable = false, unique = true, length = 160)
    private String workEmail;

    @Column(name = "personal_email", length = 160)
    private String personalEmail;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(name = "nationality", length = 60)
    private String nationality;

    @Column(name = "current_address", length = 500)
    private String currentAddress;

    @Column(name = "permanent_address", length = 500)
    private String permanentAddress;

    // ---- Job placement ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id")
    private BusinessUnit businessUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    /** Reporting manager. Drives the org chart and managers' data scope. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.PROBATION;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    // ---- Compensation headline (full structures arrive with Payroll) ----
    @Column(name = "annual_ctc", precision = 14, scale = 2)
    private BigDecimal annualCtc;

    public String getFullName() {
        return middleName == null || middleName.isBlank()
                ? firstName + " " + lastName
                : firstName + " " + middleName + " " + lastName;
    }
}
