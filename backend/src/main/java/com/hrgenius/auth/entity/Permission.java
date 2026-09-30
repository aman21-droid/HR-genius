package com.hrgenius.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fine-grained permission (e.g. EMPLOYEE_READ, PAYROLL_RUN) granted to roles.
 * Spring Security authorities are exposed as these codes so @PreAuthorize can check them.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "permission_seq_gen")
    @SequenceGenerator(name = "permission_seq_gen", sequenceName = "permission_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "description", length = 200)
    private String description;

    public Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
