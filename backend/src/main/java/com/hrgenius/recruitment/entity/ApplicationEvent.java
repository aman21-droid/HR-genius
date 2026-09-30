package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationEventType;
import com.hrgenius.recruitment.entity.RecruitmentEnums.ApplicationStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** Append-only activity trail entry for an application. */
@Getter
@Setter
@Entity
@Table(name = "application_events")
@SQLRestriction("deleted = 0")
public class ApplicationEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "application_event_seq_gen")
    @SequenceGenerator(name = "application_event_seq_gen", sequenceName = "application_event_seq", allocationSize = 1)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ApplicationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20)
    private ApplicationStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", length = 20)
    private ApplicationStage toStage;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "actor_name", length = 160)
    private String actorName;
}
