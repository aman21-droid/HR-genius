package com.hrgenius.compliance.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

/** A company policy. Acknowledgements are tracked per {@code versionNo}. */
@Getter
@Setter
@Entity
@Table(name = "policies")
@SQLRestriction("deleted = 0")
public class Policy extends BaseEntity {

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "policy_seq_gen")
    @SequenceGenerator(name = "policy_seq_gen", sequenceName = "policy_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false, length = 30, updatable = false)
    private String code;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    @Column(name = "requires_ack", nullable = false)
    private boolean requiresAck = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "published_at")
    private Instant publishedAt;
}
