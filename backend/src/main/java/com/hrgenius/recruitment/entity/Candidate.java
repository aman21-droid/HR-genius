package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.recruitment.entity.RecruitmentEnums.CandidateSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/** A person in the talent pool, unique by email and reused across applications. */
@Getter
@Setter
@Entity
@Table(name = "candidates")
@SQLRestriction("deleted = 0")
public class Candidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "candidate_seq_gen")
    @SequenceGenerator(name = "candidate_seq_gen", sequenceName = "candidate_seq", allocationSize = 1)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "current_company", length = 120)
    private String currentCompany;

    @Column(name = "current_title", length = 120)
    private String currentTitle;

    @Column(name = "total_experience", precision = 4, scale = 1)
    private BigDecimal totalExperience;

    @Column(name = "current_ctc", precision = 14, scale = 2)
    private BigDecimal currentCtc;

    @Column(name = "expected_ctc", precision = 14, scale = 2)
    private BigDecimal expectedCtc;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CandidateSource source = CandidateSource.DIRECT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_emp_id")
    private Employee referredBy;

    @Column(name = "resume_key", length = 40)
    private String resumeKey;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "resume_content_type", length = 120)
    private String resumeContentType;

    @Column(name = "resume_size")
    private Long resumeSize;

    @Column(name = "notes", length = 2000)
    private String notes;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
