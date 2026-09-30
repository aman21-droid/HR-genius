package com.hrgenius.org.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/** API payloads for the company profile and org masters. */
public final class OrgDtos {

    private OrgDtos() {
    }

    /**
     * One shape for all master types. Type-specific fields are null where they don't apply
     * (e.g. levelNo only for grades, address fields only for locations).
     */
    public record MasterDto(
            Long id, String code, String name, String description, boolean active,
            // department
            Long businessUnitId, String businessUnitName,
            Long costCenterId, String costCenterName,
            Long parentId, String parentName,
            Long headEmployeeId, String headEmployeeName,
            // grade
            Integer levelNo,
            // location
            String addressLine, String city, String state, String country, String postalCode, String timezone,
            // usage
            long employeeCount) {
    }

    public record MasterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,30}$",
                    message = "use 1-30 letters, digits, '-' or '_'") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            Boolean active,
            Long businessUnitId,
            Long costCenterId,
            Long parentId,
            Long headEmployeeId,
            @Min(1) @Max(99) Integer levelNo,
            @Size(max = 300) String addressLine,
            @Size(max = 80) String city,
            @Size(max = 80) String state,
            @Size(max = 80) String country,
            @Size(max = 20) String postalCode,
            @Size(max = 50) String timezone) {
    }

    public record LookupItem(Long id, String code, String name) {
    }

    /** Every active master in one payload, so forms need a single (cached) request. */
    public record OrgLookups(
            List<LookupItem> businessUnits,
            List<LookupItem> costCenters,
            List<LookupItem> departments,
            List<LookupItem> designations,
            List<LookupItem> grades,
            List<LookupItem> locations) {
    }

    public record CompanyDto(Long id, String name, String legalName, String registrationNo, String website,
                             String country, String currency, Integer fyStartMonth, String employeeCodePrefix) {
    }

    public record CompanyRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 200) String legalName,
            @Size(max = 60) String registrationNo,
            @Size(max = 200) String website,
            @Size(max = 80) String country,
            @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code like INR") String currency,
            @Min(1) @Max(12) Integer fyStartMonth,
            @Pattern(regexp = "^[A-Z]{1,10}$", message = "use 1-10 capital letters") String employeeCodePrefix) {
    }
}
