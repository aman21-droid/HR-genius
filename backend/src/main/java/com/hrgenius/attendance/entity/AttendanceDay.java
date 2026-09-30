package com.hrgenius.attendance.entity;

import com.hrgenius.attendance.entity.AttendanceEnums.AttendanceSource;
import com.hrgenius.attendance.entity.AttendanceEnums.AttendanceStatus;
import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

/** One employee's attendance for one day: punches, worked minutes, and resolved status. */
@Getter
@Setter
@Entity
@Table(name = "attendance_days")
@SQLRestriction("deleted = 0")
public class AttendanceDay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attendance_day_seq_gen")
    @SequenceGenerator(name = "attendance_day_seq_gen", sequenceName = "attendance_day_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in")
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status = AttendanceStatus.ABSENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AttendanceSource source = AttendanceSource.SELF;

    @Column(name = "remarks", length = 300)
    private String remarks;
}
