package com.hrgenius.employee.dto;

import com.hrgenius.employee.entity.EmployeeEnums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API payloads for employees. Entities never leave the service layer. */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /** What the caller may see of a given employee; drives field blanking in the mapper. */
    public record ViewPolicy(boolean full, boolean compensation, boolean sensitive, boolean edit) {
    }

    /** Directory row / card. Only phone-book level fields, safe for every signed-in user. */
    public record EmployeeSummaryDto(
            Long id, String employeeCode, String fullName, String firstName, String lastName,
            String workEmail, String phone,
            Long designationId, String designationName,
            Long departmentId, String departmentName,
            Long locationId, String locationName,
            String gradeCode,
            Long managerId, String managerName,
            String status, String employmentType, LocalDate dateOfJoining) {
    }

    /** Full profile. Personal/compensation fields are null when the caller lacks access. */
    public record EmployeeDetailDto(
            Long id, String employeeCode, String firstName, String middleName, String lastName, String fullName,
            String workEmail, String phone,
            // job
            Long designationId, String designationName,
            Long departmentId, String departmentName,
            Long gradeId, String gradeName,
            Long locationId, String locationName,
            Long businessUnitId, String businessUnitName,
            Long costCenterId, String costCenterName,
            Long managerId, String managerName, String managerCode,
            String employmentType, String status,
            LocalDate dateOfJoining, LocalDate probationEndDate, LocalDate confirmationDate, LocalDate exitDate,
            Integer noticePeriodDays,
            // personal (full profile only)
            String personalEmail, String gender, LocalDate dateOfBirth, String maritalStatus,
            String bloodGroup, String nationality, String currentAddress, String permanentAddress,
            // compensation (sensitive)
            BigDecimal annualCtc,
            // meta
            long directReports, boolean hasLogin,
            boolean limitedView, boolean canViewCompensation, boolean canViewSensitive, boolean canEdit) {
    }

    /** Create/update payload. Server-side rules beyond these annotations live in EmployeeService. */
    public record EmployeeRequest(
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String middleName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 160) String workEmail,
            @Email @Size(max = 160) String personalEmail,
            @Pattern(regexp = "^[+0-9 ()-]{7,30}$", message = "must be a valid phone number") String phone,
            Gender gender,
            @Past LocalDate dateOfBirth,
            MaritalStatus maritalStatus,
            @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "must be like O+ or AB-") String bloodGroup,
            @Size(max = 60) String nationality,
            @Size(max = 500) String currentAddress,
            @Size(max = 500) String permanentAddress,
            @NotNull Long departmentId,
            @NotNull Long designationId,
            Long gradeId,
            @NotNull Long locationId,
            Long managerId,
            @NotNull EmploymentType employmentType,
            EmployeeStatus status,
            @NotNull LocalDate dateOfJoining,
            LocalDate probationEndDate,
            @Min(0) @Max(365) Integer noticePeriodDays,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal annualCtc,
            /* create only: also provision a login with the EMPLOYEE role */
            boolean createLogin) {
    }

    /** Returned once on create; the temporary password is never retrievable again. */
    public record CreateEmployeeResponse(EmployeeDetailDto employee, String loginEmail, String temporaryPassword) {
    }

    /** Compact row for pickers (e.g. choose a manager). */
    public record EmployeeLookupDto(Long id, String employeeCode, String fullName, String designation) {
    }

    public record OrgChartNodeDto(Long id, String employeeCode, String name, String designation,
                                  String department, String location, Long managerId, int directReports) {
    }
}
