package com.hrgenius.org.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Pay/seniority band. levelNo orders grades (1 = most junior). */
@Getter
@Setter
@Entity
@Table(name = "grades")
@SQLRestriction("deleted = 0")
public class Grade extends MasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "grade_seq_gen")
    @SequenceGenerator(name = "grade_seq_gen", sequenceName = "grade_seq", allocationSize = 1)
    private Long id;

    @Column(name = "level_no")
    private Integer levelNo;
}
