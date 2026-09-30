package com.hrgenius.org.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Office location. Holiday calendars (Phase 3) attach per location. */
@Getter
@Setter
@Entity
@Table(name = "locations")
@SQLRestriction("deleted = 0")
public class Location extends MasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_seq_gen")
    @SequenceGenerator(name = "location_seq_gen", sequenceName = "location_seq", allocationSize = 1)
    private Long id;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "country", length = 80)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "timezone", length = 50)
    private String timezone;
}
