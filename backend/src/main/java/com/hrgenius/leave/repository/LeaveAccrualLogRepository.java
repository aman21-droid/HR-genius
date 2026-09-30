package com.hrgenius.leave.repository;

import com.hrgenius.leave.entity.LeaveAccrualLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveAccrualLogRepository extends JpaRepository<LeaveAccrualLog, Long> {

    boolean existsByEmployeeIdAndLeaveTypeIdAndPeriod(Long employeeId, Long leaveTypeId, String period);
}
