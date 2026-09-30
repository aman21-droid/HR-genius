package com.hrgenius.employee.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.EmployeeEnums.TimelineEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One entry in an employee's lifecycle timeline (joined, promoted, transferred, ...). */
@Getter
@Setter
@Entity
@Table(name = "employee_timeline")
public class TimelineEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "timeline_event_seq_gen")
    @SequenceGenerator(name = "timeline_event_seq_gen", sequenceName = "timeline_event_seq", allocationSize = 1)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private TimelineEventType eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;
}
