package com.hrgenius.compliance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable trail entry for sensitive changes (salary, bank/statutory IDs, roles, job moves)
 * and for reveals of masked data. Old/new values of sensitive fields are stored MASKED.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq_gen")
    @SequenceGenerator(name = "audit_log_seq_gen", sequenceName = "audit_log_seq", allocationSize = 1)
    private Long id;

    @Column(name = "entity", nullable = false, length = 100)
    private String entity;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "field", length = 100)
    private String field;

    @Column(name = "old_value", length = 2000)
    private String oldValue;

    @Column(name = "new_value", length = 2000)
    private String newValue;

    @Column(name = "actor", length = 120)
    private String actor;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public enum AuditAction {
        CREATE, UPDATE, DELETE, VIEW_SENSITIVE, IMPORT
    }
}
