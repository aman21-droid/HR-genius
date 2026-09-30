package com.hrgenius.org.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(name = "business_units")
@SQLRestriction("deleted = 0")
public class BusinessUnit extends MasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "business_unit_seq_gen")
    @SequenceGenerator(name = "business_unit_seq_gen", sequenceName = "business_unit_seq", allocationSize = 1)
    private Long id;
}
