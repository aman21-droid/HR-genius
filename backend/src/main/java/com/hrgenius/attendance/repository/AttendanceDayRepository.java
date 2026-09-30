package com.hrgenius.attendance.repository;

import com.hrgenius.attendance.entity.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, Long> {

    Optional<AttendanceDay> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceDay> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceDay> findByWorkDateOrderByEmployeeIdAsc(LocalDate workDate);
}
