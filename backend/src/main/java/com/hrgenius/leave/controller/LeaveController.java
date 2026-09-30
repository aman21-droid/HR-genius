package com.hrgenius.leave.controller;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.leave.dto.LeaveDtos.*;
import com.hrgenius.leave.service.LeaveAccrualService;
import com.hrgenius.leave.service.LeaveService;
import com.hrgenius.leave.service.LeaveTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Leave types (config), balances, and the apply/cancel lifecycle. */
@Tag(name = "Leave")
@RestController
@RequestMapping("/api/v1/leave")
public class LeaveController {

    private final LeaveService leaveService;
    private final LeaveTypeService leaveTypeService;
    private final LeaveAccrualService accrualService;
    private final CurrentUserService currentUser;

    public LeaveController(LeaveService leaveService, LeaveTypeService leaveTypeService,
                           LeaveAccrualService accrualService, CurrentUserService currentUser) {
        this.leaveService = leaveService;
        this.leaveTypeService = leaveTypeService;
        this.accrualService = accrualService;
        this.currentUser = currentUser;
    }

    // ---- leave types (config) ----

    @Operation(summary = "List leave types")
    @GetMapping("/types")
    public List<LeaveTypeDto> types(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return leaveTypeService.list(activeOnly);
    }

    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public LeaveTypeDto createType(@Valid @RequestBody LeaveTypeRequest req) {
        return leaveTypeService.create(req);
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public LeaveTypeDto updateType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest req) {
        return leaveTypeService.update(id, req);
    }

    @DeleteMapping("/types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public void deleteType(@PathVariable Long id) {
        leaveTypeService.delete(id);
    }

    // ---- balances ----

    @Operation(summary = "My leave balances for a year")
    @GetMapping("/balances")
    public List<LeaveBalanceDto> myBalances(@RequestParam(required = false) Integer year) {
        return leaveService.balances(me(), yearOrNow(year));
    }

    @Operation(summary = "Another employee's balances (HR)")
    @GetMapping("/balances/{employeeId}")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    public List<LeaveBalanceDto> employeeBalances(@PathVariable Long employeeId,
                                                  @RequestParam(required = false) Integer year) {
        return leaveService.balances(employeeId, yearOrNow(year));
    }

    @Operation(summary = "Manually adjust a balance (HR)")
    @PostMapping("/balances/adjust")
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public LeaveBalanceDto adjust(@Valid @RequestBody BalanceAdjustmentRequest req) {
        return leaveService.adjust(req);
    }

    // ---- requests ----

    @Operation(summary = "My leave requests")
    @GetMapping("/requests")
    public List<LeaveRequestDto> myRequests() {
        return leaveService.myRequests(me());
    }

    @Operation(summary = "Apply for leave")
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestDto apply(@Valid @RequestBody ApplyLeaveRequest req) {
        return leaveService.apply(me(), req);
    }

    @Operation(summary = "Cancel my leave request")
    @PostMapping("/requests/{id}/cancel")
    public LeaveRequestDto cancel(@PathVariable Long id) {
        return leaveService.cancel(id, me());
    }

    // ---- accrual ----

    @Operation(summary = "Run monthly accrual for a period (YYYY-MM); defaults to current month")
    @PostMapping("/accrual/run")
    @PreAuthorize("hasAuthority('LEAVE_CONFIG')")
    public Map<String, Object> runAccrual(@RequestParam(required = false) String period) {
        YearMonth ym = period == null || period.isBlank() ? YearMonth.now() : YearMonth.parse(period);
        int posted = accrualService.runForPeriod(ym);
        return Map.of("period", ym.toString(), "posted", posted);
    }

    // ---- helpers ----

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }

    private int yearOrNow(Integer year) {
        return year == null ? LocalDate.now().getYear() : year;
    }
}
