package com.hrgenius.org.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(name = "cost_centers")
@SQLRestriction("deleted = 0")
public class CostCenter extends MasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cost_center_seq_gen")
    @SequenceGenerator(name = "cost_center_seq_gen", sequenceName = "cost_center_seq", allocationSize = 1)
    private Long id;
}
