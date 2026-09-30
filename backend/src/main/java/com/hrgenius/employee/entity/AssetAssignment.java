package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One custody period of an asset. Open while {@code returnedOn} is null. */
@Getter
@Setter
@Entity
@Table(name = "asset_assignments")
public class AssetAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_assignment_seq_gen")
    @SequenceGenerator(name = "asset_assignment_seq_gen", sequenceName = "asset_assignment_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "assigned_on", nullable = false)
    private LocalDate assignedOn;

    @Column(name = "returned_on")
    private LocalDate returnedOn;

    @Column(name = "assign_notes", length = 500)
    private String assignNotes;

    @Column(name = "return_condition", length = 40)
    private String returnCondition;

    @Column(name = "return_notes", length = 500)
    private String returnNotes;
}
