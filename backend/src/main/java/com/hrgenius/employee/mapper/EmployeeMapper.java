package com.hrgenius.employee.mapper;

import com.hrgenius.employee.dto.EmployeeDtos.*;
import com.hrgenius.employee.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity -> DTO mapping. MapStruct generates null-safe nested access (e.g. a missing
 * department yields a null departmentName rather than an NPE).
 */
@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "designationId", source = "designation.id")
    @Mapping(target = "designationName", source = "designation.name")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "locationId", source = "location.id")
    @Mapping(target = "locationName", source = "location.name")
    @Mapping(target = "gradeCode", source = "grade.code")
    @Mapping(target = "managerId", source = "manager.id")
    @Mapping(target = "managerName", source = "manager.fullName")
    EmployeeSummaryDto toSummary(Employee e);

    @Mapping(target = "id", source = "e.id")
    @Mapping(target = "designationId", source = "e.designation.id")
    @Mapping(target = "designationName", source = "e.designation.name")
    @Mapping(target = "departmentId", source = "e.department.id")
    @Mapping(target = "departmentName", source = "e.department.name")
    @Mapping(target = "gradeId", source = "e.grade.id")
    @Mapping(target = "gradeName", source = "e.grade.name")
    @Mapping(target = "locationId", source = "e.location.id")
    @Mapping(target = "locationName", source = "e.location.name")
    @Mapping(target = "businessUnitId", source = "e.businessUnit.id")
    @Mapping(target = "businessUnitName", source = "e.businessUnit.name")
    @Mapping(target = "costCenterId", source = "e.costCenter.id")
    @Mapping(target = "costCenterName", source = "e.costCenter.name")
    @Mapping(target = "managerId", source = "e.manager.id")
    @Mapping(target = "managerName", source = "e.manager.fullName")
    @Mapping(target = "managerCode", source = "e.manager.employeeCode")
    // personal: only with full-profile access
    @Mapping(target = "personalEmail", expression = "java(policy.full() ? e.getPersonalEmail() : null)")
    @Mapping(target = "gender", expression = "java(policy.full() && e.getGender() != null ? e.getGender().name() : null)")
    @Mapping(target = "dateOfBirth", expression = "java(policy.full() ? e.getDateOfBirth() : null)")
    @Mapping(target = "maritalStatus", expression = "java(policy.full() && e.getMaritalStatus() != null ? e.getMaritalStatus().name() : null)")
    @Mapping(target = "bloodGroup", expression = "java(policy.full() ? e.getBloodGroup() : null)")
    @Mapping(target = "nationality", expression = "java(policy.full() ? e.getNationality() : null)")
    @Mapping(target = "currentAddress", expression = "java(policy.full() ? e.getCurrentAddress() : null)")
    @Mapping(target = "permanentAddress", expression = "java(policy.full() ? e.getPermanentAddress() : null)")
    @Mapping(target = "probationEndDate", expression = "java(policy.full() ? e.getProbationEndDate() : null)")
    @Mapping(target = "noticePeriodDays", expression = "java(policy.full() ? e.getNoticePeriodDays() : null)")
    // compensation: sensitive
    @Mapping(target = "annualCtc", expression = "java(policy.compensation() ? e.getAnnualCtc() : null)")
    // meta
    @Mapping(target = "limitedView", expression = "java(!policy.full())")
    @Mapping(target = "canViewCompensation", expression = "java(policy.compensation())")
    @Mapping(target = "canViewSensitive", expression = "java(policy.sensitive())")
    @Mapping(target = "canEdit", expression = "java(policy.edit())")
    EmployeeDetailDto toDetail(Employee e, ViewPolicy policy, long directReports, boolean hasLogin);
}
