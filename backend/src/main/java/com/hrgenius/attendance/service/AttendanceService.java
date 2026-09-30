package com.hrgenius.attendance.service;

import com.hrgenius.approval.entity.ApprovalRequest;
import com.hrgenius.approval.service.ApprovalOutcomeHandler;
import com.hrgenius.approval.service.ApprovalService;
import com.hrgenius.approval.service.Approver;
import com.hrgenius.approval.service.ApproverResolver;
import com.hrgenius.attendance.dto.AttendanceDtos.*;
import com.hrgenius.attendance.entity.AttendanceDay;
import com.hrgenius.attendance.entity.AttendanceEnums.AttendanceSource;
import com.hrgenius.attendance.entity.AttendanceEnums.AttendanceStatus;
import com.hrgenius.attendance.entity.AttendanceEnums.RegularizationStatus;
import com.hrgenius.attendance.entity.Holiday;
import com.hrgenius.attendance.entity.RegularizationRequest;
import com.hrgenius.attendance.repository.AttendanceDayRepository;
import com.hrgenius.attendance.repository.HolidayRepository;
import com.hrgenius.attendance.repository.RegularizationRequestRepository;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.leave.entity.LeaveEnums.LeaveStatus;
import com.hrgenius.leave.entity.LeaveRequest;
import com.hrgenius.leave.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Punch-based attendance plus regularization. Self check-in/out record real punches; HR can edit
 * a day directly; a regularization request routes through the approval engine and, once approved,
 * writes the corrected punches back onto the day ({@link ApprovalOutcomeHandler} for REGULARIZATION).
 */
@Service
public class AttendanceService implements ApprovalOutcomeHandler {

    public static final String SUBJECT_TYPE = "REGULARIZATION";
    private static final int HALF_DAY_MINUTES = 240;

    private final AttendanceDayRepository days;
    private final RegularizationRequestRepository regularizations;
    private final HolidayRepository holidays;
    private final LeaveRequestRepository leaveRequests;
    private final EmployeeRepository employees;
    private final ApprovalService approvals;
    private final ApproverResolver approverResolver;

    public AttendanceService(AttendanceDayRepository days, RegularizationRequestRepository regularizations,
                             HolidayRepository holidays, LeaveRequestRepository leaveRequests,
                             EmployeeRepository employees, ApprovalService approvals,
                             ApproverResolver approverResolver) {
        this.days = days;
        this.regularizations = regularizations;
        this.holidays = holidays;
        this.leaveRequests = leaveRequests;
        this.employees = employees;
        this.approvals = approvals;
        this.approverResolver = approverResolver;
    }

    // ------------------------------------------------------------------ punches

    @Transactional
    public AttendanceDayDto checkIn(Long employeeId, String remarks) {
        LocalDate today = LocalDate.now();
        AttendanceDay day = days.findByEmployeeIdAndWorkDate(employeeId, today)
                .orElseGet(() -> newDay(employeeId, today));
        if (day.getCheckIn() != null) {
            throw new BusinessException("You have already checked in today");
        }
        day.setCheckIn(Instant.now());
        day.setStatus(AttendanceStatus.PRESENT);
        day.setSource(AttendanceSource.SELF);
        if (remarks != null && !remarks.isBlank()) {
            day.setRemarks(remarks);
        }
        return toDto(day.getId() == null ? days.save(day) : day);
    }

    @Transactional
    public AttendanceDayDto checkOut(Long employeeId) {
        LocalDate today = LocalDate.now();
        AttendanceDay day = days.findByEmployeeIdAndWorkDate(employeeId, today)
                .orElseThrow(() -> new BusinessException("You have not checked in today"));
        if (day.getCheckIn() == null) {
            throw new BusinessException("You have not checked in today");
        }
        if (day.getCheckOut() != null) {
            throw new BusinessException("You have already checked out today");
        }
        day.setCheckOut(Instant.now());
        recompute(day);
        return toDto(day);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<AttendanceDayDto> range(Long employeeId, LocalDate from, LocalDate to) {
        return days.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to)
                .stream().map(this::toDto).toList();
    }

    /** A day-by-day calendar: real punches where present, else HOLIDAY / WEEKEND / ON_LEAVE / ABSENT. */
    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(Long employeeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("End date cannot be before start date");
        }
        Map<LocalDate, AttendanceDay> byDate = new HashMap<>();
        days.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to)
                .forEach(d -> byDate.put(d.getWorkDate(), d));

        Map<LocalDate, String> holidayNames = new HashMap<>();
        for (Holiday h : holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)) {
            holidayNames.putIfAbsent(h.getHolidayDate(), h.getName());
        }

        Set<LocalDate> onLeave = new HashSet<>();
        for (LeaveRequest lr : leaveRequests
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, LeaveStatus.APPROVED, to, from)) {
            for (LocalDate d = lr.getStartDate(); !d.isAfter(lr.getEndDate()); d = d.plusDays(1)) {
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    onLeave.add(d);
                }
            }
        }

        List<CalendarDay> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceDay rec = byDate.get(d);
            if (rec != null && rec.getCheckIn() != null) {
                out.add(new CalendarDay(d, rec.getStatus().name(), rec.getCheckIn(), rec.getCheckOut(),
                        rec.getWorkedMinutes(), rec.getRemarks()));
            } else if (holidayNames.containsKey(d)) {
                out.add(new CalendarDay(d, AttendanceStatus.HOLIDAY.name(), null, null, null, holidayNames.get(d)));
            } else if (isWeekend(d)) {
                out.add(new CalendarDay(d, AttendanceStatus.WEEKEND.name(), null, null, null, null));
            } else if (onLeave.contains(d)) {
                out.add(new CalendarDay(d, AttendanceStatus.ON_LEAVE.name(), null, null, null, null));
            } else if (rec != null) {
                out.add(new CalendarDay(d, rec.getStatus().name(), null, null, rec.getWorkedMinutes(),
                        rec.getRemarks()));
            } else {
                out.add(new CalendarDay(d, AttendanceStatus.ABSENT.name(), null, null, null, null));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ HR edit

    @Transactional
    public AttendanceDayDto adminEdit(Long employeeId, LocalDate date, AttendanceEditRequest req) {
        AttendanceDay day = days.findByEmployeeIdAndWorkDate(employeeId, date)
                .orElseGet(() -> days.save(newDay(employeeId, date)));
        day.setCheckIn(req.checkIn());
        day.setCheckOut(req.checkOut());
        day.setRemarks(req.remarks());
        day.setSource(AttendanceSource.ADMIN);
        if (req.status() != null && !req.status().isBlank()) {
            day.setStatus(AttendanceStatus.valueOf(req.status()));
            day.setWorkedMinutes(minutesBetween(req.checkIn(), req.checkOut()));
        } else {
            recompute(day);
        }
        return toDto(day);
    }

    // ------------------------------------------------------------ regularization

    @Transactional(readOnly = true)
    public List<RegularizationDto> myRegularizations(Long employeeId) {
        return regularizations.findByEmployeeIdOrderByWorkDateDesc(employeeId).stream().map(this::toDto).toList();
    }

    @Transactional
    public RegularizationDto requestRegularization(Long employeeId, RegularizationRequestBody body) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + employeeId + " not found"));
        if (body.requestedCheckIn() == null && body.requestedCheckOut() == null) {
            throw new BadRequestException("Provide at least one of check-in or check-out time");
        }
        RegularizationRequest reg = new RegularizationRequest();
        reg.setEmployee(employee);
        reg.setWorkDate(body.workDate());
        reg.setRequestedCheckIn(body.requestedCheckIn());
        reg.setRequestedCheckOut(body.requestedCheckOut());
        reg.setReason(body.reason());
        reg.setStatus(RegularizationStatus.PENDING);
        regularizations.save(reg);

        List<Approver> chain = approverResolver.forEmployee(employeeId);
        String title = employee.getFullName() + " · Attendance regularization · " + body.workDate();
        ApprovalRequest approval = approvals.submit(SUBJECT_TYPE, reg.getId(), employeeId, title, chain);
        reg.setApprovalRequestId(approval.getId());
        return toDto(reg);
    }

    // --------------------------------------------------- approval callbacks

    @Override
    public String subjectType() {
        return SUBJECT_TYPE;
    }

    @Override
    @Transactional
    public void onApproved(ApprovalRequest request) {
        RegularizationRequest reg = regularizations.findById(request.getSubjectId()).orElse(null);
        if (reg == null || reg.getStatus() != RegularizationStatus.PENDING) {
            return;
        }
        AttendanceDay day = days.findByEmployeeIdAndWorkDate(reg.getEmployee().getId(), reg.getWorkDate())
                .orElseGet(() -> days.save(newDay(reg.getEmployee().getId(), reg.getWorkDate())));
        if (reg.getRequestedCheckIn() != null) {
            day.setCheckIn(reg.getRequestedCheckIn());
        }
        if (reg.getRequestedCheckOut() != null) {
            day.setCheckOut(reg.getRequestedCheckOut());
        }
        day.setSource(AttendanceSource.REGULARIZED);
        recompute(day);
        reg.setStatus(RegularizationStatus.APPROVED);
        reg.setDecidedAt(Instant.now());
    }

    @Override
    @Transactional
    public void onRejected(ApprovalRequest request, String reason) {
        RegularizationRequest reg = regularizations.findById(request.getSubjectId()).orElse(null);
        if (reg == null || reg.getStatus() != RegularizationStatus.PENDING) {
            return;
        }
        reg.setStatus(RegularizationStatus.REJECTED);
        reg.setDecidedAt(Instant.now());
    }

    // ------------------------------------------------------------------ helpers

    private AttendanceDay newDay(Long employeeId, LocalDate date) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + employeeId + " not found"));
        AttendanceDay day = new AttendanceDay();
        day.setEmployee(employee);
        day.setWorkDate(date);
        day.setStatus(AttendanceStatus.ABSENT);
        day.setSource(AttendanceSource.SELF);
        return day;
    }

    /** Recompute worked minutes and PRESENT/HALF_DAY from the punches. */
    private void recompute(AttendanceDay day) {
        int minutes = minutesBetween(day.getCheckIn(), day.getCheckOut());
        day.setWorkedMinutes(minutes);
        if (day.getCheckIn() == null) {
            day.setStatus(AttendanceStatus.ABSENT);
        } else if (day.getCheckOut() == null) {
            day.setStatus(AttendanceStatus.PRESENT);
        } else {
            day.setStatus(minutes < HALF_DAY_MINUTES ? AttendanceStatus.HALF_DAY : AttendanceStatus.PRESENT);
        }
    }

    private int minutesBetween(Instant in, Instant out) {
        if (in == null || out == null || out.isBefore(in)) {
            return 0;
        }
        return (int) Duration.between(in, out).toMinutes();
    }

    private boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private AttendanceDayDto toDto(AttendanceDay d) {
        Employee e = d.getEmployee();
        return new AttendanceDayDto(d.getId(), e.getId(), e.getFullName(), d.getWorkDate(),
                d.getCheckIn(), d.getCheckOut(), d.getWorkedMinutes(), d.getStatus().name(),
                d.getSource().name(), d.getRemarks());
    }

    private RegularizationDto toDto(RegularizationRequest r) {
        Employee e = r.getEmployee();
        return new RegularizationDto(r.getId(), e.getId(), e.getFullName(), r.getWorkDate(),
                r.getRequestedCheckIn(), r.getRequestedCheckOut(), r.getReason(), r.getStatus().name(),
                r.getApprovalRequestId(), r.getDecidedAt(), r.getCreatedAt());
    }
}
