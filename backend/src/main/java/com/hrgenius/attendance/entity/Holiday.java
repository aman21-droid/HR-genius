package com.hrgenius.attendance.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.org.entity.Location;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/** A holiday on the company calendar. A null location means it applies company-wide. */
@Getter
@Setter
@Entity
@Table(name = "holidays")
@SQLRestriction("deleted = 0")
public class Holiday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "holiday_seq_gen")
    @SequenceGenerator(name = "holiday_seq_gen", sequenceName = "holiday_seq", allocationSize = 1)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Optional/restricted holiday the employee may choose, vs a mandatory closure. */
    @Column(name = "optional_holiday", nullable = false)
    private boolean optionalHoliday = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "year_no", nullable = false)
    private int year;
}
