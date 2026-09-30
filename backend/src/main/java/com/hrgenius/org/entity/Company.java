package com.hrgenius.org.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Single-row company profile and org-wide settings. */
@Getter
@Setter
@Entity
@Table(name = "company")
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company_seq_gen")
    @SequenceGenerator(name = "company_seq_gen", sequenceName = "company_seq", allocationSize = 1)
    private Long id;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "registration_no", length = 60)
    private String registrationNo;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "country", length = 80)
    private String country;

    /** ISO 4217 code, e.g. INR. */
    @Column(name = "currency", length = 3)
    private String currency;

    /** Month the financial year starts (1-12); India default is April (4). */
    @Column(name = "fy_start_month")
    private Integer fyStartMonth;

    /** Prefix used when auto-generating employee codes, e.g. "EMP". */
    @Column(name = "employee_code_prefix", length = 10)
    private String employeeCodePrefix;
}
