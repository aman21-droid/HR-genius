package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(name = "emergency_contacts")
@SQLRestriction("deleted = 0")
public class EmergencyContact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "emergency_contact_seq_gen")
    @SequenceGenerator(name = "emergency_contact_seq_gen", sequenceName = "emergency_contact_seq", allocationSize = 1)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "relationship", nullable = false, length = 40)
    private String relationship;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
