package com.hrgenius.leave.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** API payloads for leave types, balances, and requests. */
public final class LeaveDtos {

    private LeaveDtos() {
    }

    public record LeaveTypeDto(
            Long id, String code, String name, String description, String color, boolean paid,
            BigDecimal annualEntitlement, String accrualMethod, BigDecimal accrualRate,
            BigDecimal carryForwardCap, BigDecimal maxBalance, boolean allowHalfDay,
            boolean encashable, boolean requiresApproval, boolean active) {
    }

    public record LeaveTypeRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
                    message = "use 1-20 letters, digits, '-' or '_'") String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @Size(max = 20) String color,
            Boolean paid,
            @NotNull @DecimalMin("0.0") @DecimalMax("366.0") BigDecimal annualEntitlement,
            @NotNull @Pattern(regexp = "NONE|MONTHLY|ANNUAL") String accrualMethod,
            @NotNull @DecimalMin("0.0") @DecimalMax("366.0") BigDecimal accrualRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("366.0") BigDecimal carryForwardCap,
            @DecimalMin("0.0") @DecimalMax("366.0") BigDecimal maxBalance,
            Boolean allowHalfDay,
            Boolean encashable,
            Boolean requiresApproval,
            Boolean active) {
    }

    /** One row of an employee's balance sheet for a year. */
    public record LeaveBalanceDto(
            Long leaveTypeId, String code, String name, String color, int year,
            BigDecimal opening, BigDecimal accrued, BigDecimal used, BigDecimal pending,
            BigDecimal adjustment, BigDecimal available, BigDecimal annualEntitlement) {
    }

    public record LeaveRequestDto(
            Long id, Long employeeId, String employeeName,
            Long leaveTypeId, String leaveTypeCode, String leaveTypeName, String color,
            LocalDate startDate, LocalDate endDate, boolean halfDayStart, boolean halfDayEnd,
            BigDecimal days, String reason, String status, Long approvalRequestId,
            Instant decidedAt, Instant createdAt) {
    }

    public record ApplyLeaveRequest(
            @NotNull Long leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Boolean halfDayStart,
            Boolean halfDayEnd,
            @Size(max = 500) String reason) {
    }

    /** HR manual balance correction (adjustment is signed). */
    public record BalanceAdjustmentRequest(
            @NotNull Long employeeId,
            @NotNull Long leaveTypeId,
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull BigDecimal amount,
            @Size(max = 300) String note) {
    }
}
