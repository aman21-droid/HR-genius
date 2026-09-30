package com.hrgenius.org.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Common shape of every org master (business unit, cost center, department, designation,
 * grade, location): a unique business code, a display name, and an active flag.
 * Soft delete comes from {@link BaseEntity}; subclasses add @SQLRestriction to hide deleted rows.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class MasterEntity extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public abstract Long getId();
}
