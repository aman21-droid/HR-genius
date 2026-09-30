package com.hrgenius.org.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(name = "designations")
@SQLRestriction("deleted = 0")
public class Designation extends MasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "designation_seq_gen")
    @SequenceGenerator(name = "designation_seq_gen", sequenceName = "designation_seq", allocationSize = 1)
    private Long id;
}
