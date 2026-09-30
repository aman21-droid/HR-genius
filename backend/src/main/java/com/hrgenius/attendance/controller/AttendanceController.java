package com.hrgenius.attendance.controller;

import com.hrgenius.attendance.dto.AttendanceDtos.*;
import com.hrgenius.attendance.service.AttendanceService;
import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Self-service punches and calendar, plus HR editing and regularization. */
@Tag(name = "Attendance")
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendance;
    private final CurrentUserService currentUser;

    public AttendanceController(AttendanceService attendance, CurrentUserService currentUser) {
        this.attendance = attendance;
        this.currentUser = currentUser;
    }

    // ---- self-service ----

    @Operation(summary = "Check in for today")
    @PostMapping("/check-in")
    public AttendanceDayDto checkIn(@RequestBody(required = false) @Valid PunchRequest req) {
        return attendance.checkIn(me(), req == null ? null : req.remarks());
    }

    @Operation(summary = "Check out for today")
    @PostMapping("/check-out")
    public AttendanceDayDto checkOut() {
        return attendance.checkOut(me());
    }

    @Operation(summary = "My attendance calendar between two dates")
    @GetMapping("/calendar")
    public List<CalendarDay> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.calendar(me(), from, to);
    }

    @Operation(summary = "My raw attendance records between two dates")
    @GetMapping("/days")
    public List<AttendanceDayDto> myDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.range(me(), from, to);
    }

    // ---- regularization (self) ----

    @Operation(summary = "My regularization requests")
    @GetMapping("/regularizations")
    public List<RegularizationDto> myRegularizations() {
        return attendance.myRegularizations(me());
    }

    @Operation(summary = "Request a regularization for a past day")
    @PostMapping("/regularizations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegularizationDto regularize(@Valid @RequestBody RegularizationRequestBody body) {
        return attendance.requestRegularization(me(), body);
    }

    // ---- HR ----

    @Operation(summary = "An employee's attendance records (HR)")
    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceDayDto> employeeDays(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.range(employeeId, from, to);
    }

    @Operation(summary = "Set or edit an employee's day (HR)")
    @PutMapping("/employees/{employeeId}/{date}")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public AttendanceDayDto edit(
            @PathVariable Long employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AttendanceEditRequest req) {
        return attendance.adminEdit(employeeId, date, req);
    }

    // ---- helpers ----

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
