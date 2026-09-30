package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.EmploymentType;
import com.hrgenius.org.entity.Department;
import com.hrgenius.org.entity.Designation;
import com.hrgenius.org.entity.Grade;
import com.hrgenius.org.entity.Location;
import com.hrgenius.recruitment.entity.RecruitmentEnums.RequisitionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A headcount request for a role. It opens for applications once its approval flow
 * (subject type REQUISITION) resolves; {@code filled} counts hires against {@code openings}.
 */
@Getter
@Setter
@Entity
@Table(name = "job_requisitions")
@SQLRestriction("deleted = 0")
public class JobRequisition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "requisition_seq_gen")
    @SequenceGenerator(name = "requisition_seq_gen", sequenceName = "requisition_seq", allocationSize = 1)
    private Long id;

    @Column(name = "req_code", nullable = false, length = 20, updatable = false)
    private String reqCode;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hiring_manager_id", nullable = false)
    private Employee hiringManager;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "openings", nullable = false)
    private int openings = 1;

    @Column(name = "filled", nullable = false)
    private int filled = 0;

    @Column(name = "min_experience", precision = 4, scale = 1)
    private BigDecimal minExperience;

    @Column(name = "max_experience", precision = 4, scale = 1)
    private BigDecimal maxExperience;

    @Column(name = "salary_min", precision = 14, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 14, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "skills", length = 500)
    private String skills;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "publish_on_careers", nullable = false)
    private boolean publishOnCareers = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public int getRemaining() {
        return Math.max(0, openings - filled);
    }
}
