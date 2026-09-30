package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.EmployeeEnums.EmploymentType;
import com.hrgenius.org.entity.Department;
import com.hrgenius.org.entity.Designation;
import com.hrgenius.org.entity.Grade;
import com.hrgenius.org.entity.Location;
import com.hrgenius.recruitment.entity.RecruitmentEnums.OfferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A job offer for an application; approval runs through the engine (subject type OFFER). */
@Getter
@Setter
@Entity
@Table(name = "offers")
@SQLRestriction("deleted = 0")
public class Offer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "offer_seq_gen")
    @SequenceGenerator(name = "offer_seq_gen", sequenceName = "offer_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "annual_ctc", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualCtc;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OfferStatus status = OfferStatus.DRAFT;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;
}
