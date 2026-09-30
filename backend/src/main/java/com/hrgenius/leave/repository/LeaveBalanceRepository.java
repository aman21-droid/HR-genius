package com.hrgenius.leave.repository;

import com.hrgenius.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, int year);

    List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(Long employeeId, int year);

    List<LeaveBalance> findByLeaveTypeIdAndYear(Long leaveTypeId, int year);

    boolean existsByLeaveTypeIdAndUsedGreaterThan(Long leaveTypeId, java.math.BigDecimal used);
}
