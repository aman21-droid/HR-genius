package com.hrgenius.attendance.repository;

import com.hrgenius.attendance.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByYearOrderByHolidayDateAsc(int year);

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    /** Mandatory holidays only (optional/restricted ones don't block attendance by default). */
    List<Holiday> findByOptionalHolidayFalseAndHolidayDateBetween(LocalDate from, LocalDate to);
}
