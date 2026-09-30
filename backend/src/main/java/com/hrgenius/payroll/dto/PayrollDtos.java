package com.hrgenius.payroll.dto;

import com.hrgenius.payroll.calc.PayrollCalculator.CalcType;
import com.hrgenius.payroll.entity.PayrollAdjustment.AdjustmentType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for the payroll API. */
public final class PayrollDtos {

    private PayrollDtos() {
    }

    public record ComponentDto(Long id, String code, String name, String calcType, BigDecimal calcValue,
                               boolean taxable, int sortOrder, boolean active) {
    }

    public record ComponentRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,20}$", message = "use 2-20 capital letters, digits or _") String code,
            @NotBlank @Size(max = 80) String name,
            @NotNull CalcType calcType,
            @NotNull @DecimalMin("0.0") @DecimalMax("10000000.0") BigDecimal calcValue,
            Boolean taxable,
            @NotNull @Min(0) @Max(999) Integer sortOrder,
            Boolean active) {
    }

    public record RunDto(Long id, String period, LocalDate periodStart, LocalDate periodEnd, String status,
                         int employeeCount, BigDecimal totalGross, BigDecimal totalDeductions, BigDecimal totalNet,
                         BigDecimal totalEmployerCost, Long approvalRequestId, Instant calculatedAt,
                         Instant approvedAt, Instant paidAt, String paymentReference, String notes,
                         int adjustments, Instant createdAt) {
    }

    public record CreateRunRequest(
            @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "use YYYY-MM") String period,
            @Size(max = 500) String notes) {
    }

    public record MarkPaidRequest(@NotBlank @Size(max = 80) String paymentReference) {
    }

    /** A row of the run's payslip table. */
    public record PayslipSummaryDto(Long id, Long employeeId, String employeeCode, String employeeName,
                                    String department, String designation, BigDecimal paidDays, BigDecimal lopDays,
                                    BigDecimal grossEarnings, BigDecimal totalDeductions, BigDecimal netPay,
                                    boolean bankDetailsMissing) {
    }

    public record PayslipLineDto(String code, String name, String type, BigDecimal amount) {
    }

    public record PayslipDto(Long id, Long runId, String period, String runStatus, Long employeeId, String employeeCode,
                             String employeeName, String designation, String department, String location,
                             LocalDate dateOfJoining, String pan, String uan, int daysInPeriod, BigDecimal lopDays,
                             BigDecimal paidDays, BigDecimal annualCtc, BigDecimal grossEarnings,
                             BigDecimal totalDeductions, BigDecimal netPay, BigDecimal employerPf,
                             BigDecimal employerEsi, String taxRegime, String bankName, String accountMasked,
                             String bankIfsc, List<PayslipLineDto> earnings, List<PayslipLineDto> deductions,
                             List<PayslipLineDto> employer) {
    }

    public record AdjustmentDto(Long id, Long employeeId, String employeeCode, String employeeName, String type,
                                String label, BigDecimal amount, boolean taxable) {
    }

    public record AdjustmentRequest(
            @NotNull Long employeeId,
            @NotNull AdjustmentType type,
            @NotBlank @Size(max = 80) String label,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            Boolean taxable) {
    }

    /** An employee's own salary structure at full month, derived from their CTC. */
    public record StructureDto(BigDecimal annualCtc, BigDecimal monthlyGross, BigDecimal monthlyNet,
                               String taxRegime, List<PayslipLineDto> earnings, List<PayslipLineDto> deductions,
                               List<PayslipLineDto> employer) {
    }
}
