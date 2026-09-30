package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A company asset (laptop, ID card, ...). {@code currentEmployeeId} is denormalised from the
 * open assignment so "who has this?" and "what does this person hold?" are single lookups.
 */
@Getter
@Setter
@Entity
@Table(name = "assets")
@SQLRestriction("deleted = 0")
public class Asset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_seq_gen")
    @SequenceGenerator(name = "asset_seq_gen", sequenceName = "asset_seq", allocationSize = 1)
    private Long id;

    @Column(name = "asset_tag", nullable = false, unique = true, length = 40)
    private String assetTag;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private AssetCategory category;

    @Column(name = "serial_number", length = 80)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "current_employee_id")
    private Long currentEmployeeId;

    public enum AssetCategory { LAPTOP, MONITOR, PHONE, ID_CARD, ACCESS_CARD, HEADSET, OTHER }

    public enum AssetStatus { AVAILABLE, ASSIGNED, IN_REPAIR, RETIRED }
}
