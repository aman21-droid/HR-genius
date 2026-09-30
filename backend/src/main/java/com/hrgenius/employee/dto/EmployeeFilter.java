package com.hrgenius.employee.dto;

import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.entity.EmployeeEnums.EmploymentType;

/**
 * Directory/list filters. All optional.
 *
 * @param includeExited only honoured for full-access HR roles
 * @param teamOnly      restrict to the caller's reporting tree (manager "My team" view)
 */
public record EmployeeFilter(
        String search,
        Long departmentId,
        Long designationId,
        Long locationId,
        Long gradeId,
        Long managerId,
        EmployeeStatus status,
        EmploymentType employmentType,
        boolean includeExited,
        boolean teamOnly) {
}
