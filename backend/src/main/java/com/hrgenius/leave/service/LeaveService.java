package com.hrgenius.leave.service;

import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.service.ApprovalOutcomeHandler;
import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.approval.service.Approver;
import com.hrgenius.approval.service.ApproverResolver;
import com.hrgenius.attendance.repository.HolidayRepository;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.leave.dto.LeaveDtos.ApplyLeaveRequest;
import com.hrgenius.leave.dto.LeaveDtos.BalanceAdjustmentRequest;
import com.hrgenius.leave.dto.LeaveDtos.LeaveBalanceDto;
import com.hrgenius.leave.dto.LeaveDtos.LeaveRequestDto;
import com.hrgenius.leave.entity.LeaveBalance;
import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import com.hrgenius.leave.entity.LeaveRequest;
import com.hrgenius.leave.entity.LeaveType;
import com.hrgenius.leave.repository.LeaveBalanceRepository;
import com.hrgenius.leave.repository.LeaveRequestRepository;
import com.hrgenius.leave.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Leave application lifecycle and balance accounting. Applying reserves the days as {@code pending}
 * and opens an approval flow; the engine calls back here ({@link ApprovalOutcomeHandler}) to convert
 * pending to {@code used} on approval, or release it on rejection.
 */
@Service
public class LeaveService implements ApprovalOutcomeHandler {

    public static final String SUBJECT_TYPE = "LEAVE";
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final EmployeeRepository employees;
    private final HolidayRepository holidays;
    private final ApprovalService approvals;
    private final ApproverResolver approverResolver;

    public LeaveService(LeaveRequestRepository requests, LeaveBalanceRepository balances,
                        LeaveTypeRepository types, EmployeeRepository employees, HolidayRepository holidays,
                        ApprovalService approvals, ApproverResolver approverResolver) {
        this.requests = requests;
        this.balances = balances;
        this.types = types;
        this.employees = employees;
        this.holidays = holidays;
        this.approvals = approvals;
        this.approverResolver = approverResolver;
    }

    // ------------------------------------------------------------------ apply

    @Transactional
    public LeaveRequestDto apply(Long employeeId, ApplyLeaveRequest req) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + employeeId + " not found"));
        LeaveType type = types.findById(req.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type " + req.leaveTypeId() + " not found"));
        if (!type.isActive()) {
            throw new BusinessException("Leave type '" + type.getName() + "' is not active");
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        boolean halfStart = Boolean.TRUE.equals(req.halfDayStart());
        boolean halfEnd = Boolean.TRUE.equals(req.halfDayEnd());
        if ((halfStart || halfEnd) && !type.isAllowHalfDay()) {
            throw new BadRequestException("Half-day is not allowed for " + type.getName());
        }

        BigDecimal days = workingDays(req.startDate(), req.endDate(), halfStart, halfEnd);
        if (days.signum() <= 0) {
            throw new BadRequestException("The selected range has no working days");
        }
        if (requests.existsByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employeeId, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), req.endDate(), req.startDate())) {
            throw new BusinessException("You already have a leave request overlapping these dates");
        }

        int year = req.startDate().getYear();
        LeaveBalance balance = balances
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), year)
                .orElse(null);
        if (balance != null && balance.getAvailable().compareTo(days) < 0) {
            throw new BusinessException("Insufficient " + type.getName() + " balance: "
                    + balance.getAvailable() + " available, " + days + " requested");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(type);
        lr.setStartDate(req.startDate());
        lr.setEndDate(req.endDate());
        lr.setHalfDayStart(halfStart);
        lr.setHalfDayEnd(halfEnd);
        lr.setDays(days);
        lr.setReason(req.reason());
        lr.setStatus(LeaveStatus.PENDING);
        requests.save(lr);

        if (balance != null) {
            balance.setPending(balance.getPending().add(days));
        }

        List<Approver> chain = type.isRequiresApproval() ? approverResolver.forEmployee(employeeId) : List.of();
        String title = employee.getFullName() + " · " + type.getName() + " · " + days + "d ("
                + req.startDate() + " → " + req.endDate() + ")";
        ApprovalRequest approval = approvals.submit(SUBJECT_TYPE, lr.getId(), employeeId, title, chain);
        lr.setApprovalRequestId(approval.getId());

        return toDto(lr);
    }

    // ------------------------------------------------------------------ cancel

    @Transactional
    public LeaveRequestDto cancel(Long leaveId, Long actingEmployeeId) {
        LeaveRequest lr = requests.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request " + leaveId + " not found"));
        if (!lr.getEmployee().getId().equals(actingEmployeeId)) {
            throw new BusinessException("You can only cancel your own leave");
        }
        if (lr.getStatus() == LeaveStatus.REJECTED || lr.getStatus() == LeaveStatus.CANCELLED) {
            throw new BusinessException("This request is already " + lr.getStatus().name().toLowerCase());
        }

        LeaveBalance balance = balanceFor(lr);
        if (lr.getStatus() == LeaveStatus.PENDING) {
            if (balance != null) {
                balance.setPending(balance.getPending().subtract(lr.getDays()));
            }
            approvals.cancelBySubject(SUBJECT_TYPE, lr.getId(), "Cancelled by requester");
        } else if (lr.getStatus() == LeaveStatus.APPROVED) {
            if (balance != null) {
                balance.setUsed(balance.getUsed().subtract(lr.getDays()));
            }
        }
        lr.setStatus(LeaveStatus.CANCELLED);
        lr.setDecidedAt(Instant.now());
        return toDto(lr);
    }

    // --------------------------------------------------- approval callbacks

    @Override
    public String subjectType() {
        return SUBJECT_TYPE;
    }

    @Override
    @Transactional
    public void onApproved(ApprovalRequest request) {
        LeaveRequest lr = requests.findById(request.getSubjectId()).orElse(null);
        if (lr == null || lr.getStatus() != LeaveStatus.PENDING) {
            return;
        }
        LeaveBalance balance = balanceFor(lr);
        if (balance != null) {
            balance.setPending(balance.getPending().subtract(lr.getDays()));
            balance.setUsed(balance.getUsed().add(lr.getDays()));
        }
        lr.setStatus(LeaveStatus.APPROVED);
        lr.setDecidedAt(Instant.now());
    }

    @Override
    @Transactional
    public void onRejected(ApprovalRequest request, String reason) {
        LeaveRequest lr = requests.findById(request.getSubjectId()).orElse(null);
        if (lr == null || lr.getStatus() != LeaveStatus.PENDING) {
            return;
        }
        LeaveBalance balance = balanceFor(lr);
        if (balance != null) {
            balance.setPending(balance.getPending().subtract(lr.getDays()));
        }
        lr.setStatus(LeaveStatus.REJECTED);
        lr.setDecidedAt(Instant.now());
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> myRequests(Long employeeId) {
        return requests.findByEmployeeIdOrderByStartDateDesc(employeeId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> balances(Long employeeId, int year) {
        return balances.findByEmployeeIdAndYearOrderByLeaveTypeIdAsc(employeeId, year).stream()
                .map(b -> {
                    LeaveType t = b.getLeaveType();
                    return new LeaveBalanceDto(t.getId(), t.getCode(), t.getName(), t.getColor(), b.getYear(),
                            b.getOpening(), b.getAccrued(), b.getUsed(), b.getPending(), b.getAdjustment(),
                            b.getAvailable(), t.getAnnualEntitlement());
                })
                .toList();
    }

    /** HR manual correction: creates the balance row if missing, adds a signed adjustment. */
    @Transactional
    public LeaveBalanceDto adjust(BalanceAdjustmentRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + req.employeeId() + " not found"));
        LeaveType type = types.findById(req.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type " + req.leaveTypeId() + " not found"));
        LeaveBalance b = balances.findByEmployeeIdAndLeaveTypeIdAndYear(req.employeeId(), req.leaveTypeId(), req.year())
                .orElseGet(() -> {
                    LeaveBalance nb = new LeaveBalance();
                    nb.setEmployee(employee);
                    nb.setLeaveType(type);
                    nb.setYear(req.year());
                    return balances.save(nb);
                });
        b.setAdjustment(b.getAdjustment().add(req.amount()));
        return new LeaveBalanceDto(type.getId(), type.getCode(), type.getName(), type.getColor(), b.getYear(),
                b.getOpening(), b.getAccrued(), b.getUsed(), b.getPending(), b.getAdjustment(),
                b.getAvailable(), type.getAnnualEntitlement());
    }

    // ------------------------------------------------------------------ helpers

    /** Working days in [start,end]: Mon-Fri excluding mandatory holidays, minus half-day flags. */
    BigDecimal workingDays(LocalDate start, LocalDate end, boolean halfStart, boolean halfEnd) {
        Set<LocalDate> holidayDates = new HashSet<>();
        holidays.findByOptionalHolidayFalseAndHolidayDateBetween(start, end)
                .forEach(h -> holidayDates.add(h.getHolidayDate()));

        BigDecimal count = BigDecimal.ZERO;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWorkingDay(d, holidayDates)) {
                count = count.add(BigDecimal.ONE);
            }
        }
        if (count.signum() == 0) {
            return count;
        }
        if (halfStart && isWorkingDay(start, holidayDates)) {
            count = count.subtract(HALF);
        }
        if (halfEnd && !end.isEqual(start) && isWorkingDay(end, holidayDates)) {
            count = count.subtract(HALF);
        }
        return count;
    }

    private boolean isWorkingDay(LocalDate d, Set<LocalDate> holidayDates) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY
                && d.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidayDates.contains(d);
    }

    private LeaveBalance balanceFor(LeaveRequest lr) {
        return balances.findByEmployeeIdAndLeaveTypeIdAndYear(
                lr.getEmployee().getId(), lr.getLeaveType().getId(), lr.getStartDate().getYear()).orElse(null);
    }

    private LeaveRequestDto toDto(LeaveRequest lr) {
        LeaveType t = lr.getLeaveType();
        Employee e = lr.getEmployee();
        return new LeaveRequestDto(lr.getId(), e.getId(), e.getFullName(),
                t.getId(), t.getCode(), t.getName(), t.getColor(),
                lr.getStartDate(), lr.getEndDate(), lr.isHalfDayStart(), lr.isHalfDayEnd(),
                lr.getDays(), lr.getReason(), lr.getStatus().name(), lr.getApprovalRequestId(),
                lr.getDecidedAt(), lr.getCreatedAt());
    }
}
