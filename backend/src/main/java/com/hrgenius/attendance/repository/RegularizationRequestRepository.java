package com.hrgenius.attendance.repository;

import com.hrgenius.attendance.entity.RegularizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegularizationRequestRepository extends JpaRepository<RegularizationRequest, Long> {

    List<RegularizationRequest> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);
}
