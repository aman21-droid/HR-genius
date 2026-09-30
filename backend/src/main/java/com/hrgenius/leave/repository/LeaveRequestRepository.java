package com.hrgenius.leave.repository;

import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import com.hrgenius.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    /** Approved leave overlapping a date window, for attendance status resolution. */
    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveStatus status, LocalDate onOrBefore, LocalDate onOrAfter);

    /** Overlap check to block double-booking (any active request touching the window). */
    boolean existsByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, List<LeaveStatus> statuses, LocalDate onOrBefore, LocalDate onOrAfter);
}
