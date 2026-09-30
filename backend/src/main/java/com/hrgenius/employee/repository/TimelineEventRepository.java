package com.hrgenius.employee.repository;

import com.hrgenius.employee.entity.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    List<TimelineEvent> findByEmployeeIdOrderByEventDateDescIdDesc(Long employeeId);
}
