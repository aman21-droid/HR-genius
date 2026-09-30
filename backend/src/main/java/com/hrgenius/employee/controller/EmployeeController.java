package com.hrgenius.employee.controller;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.web.Downloads;
import com.hrgenius.employee.dto.EmployeeDtos.*;
import com.hrgenius.employee.dto.EmployeeFilter;
import com.hrgenius.employee.dto.ImportDtos.ImportReport;
import com.hrgenius.employee.entity.EmployeeEnums.EmployeeStatus;
import com.hrgenius.employee.entity.EmployeeEnums.EmploymentType;
import com.hrgenius.employee.service.EmployeeAccessService;
import com.hrgenius.employee.service.EmployeeExcelService;
import com.hrgenius.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Employee directory, profiles and bulk operations. Row-level visibility is enforced in the
 * service layer (see EmployeeAccessService), so every signed-in user can call the read endpoints
 * and simply receives what they are allowed to see.
 */
@Tag(name = "Employees")
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeExcelService excelService;
    private final EmployeeAccessService access;

    public EmployeeController(EmployeeService employeeService, EmployeeExcelService excelService,
                              EmployeeAccessService access) {
        this.employeeService = employeeService;
        this.excelService = excelService;
        this.access = access;
    }

    @Operation(summary = "Search the employee directory (paged, filterable, sortable)")
    @GetMapping
    public PageResponse<EmployeeSummaryDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(defaultValue = "false") boolean includeExited,
            @RequestParam(defaultValue = "false") boolean teamOnly,
            @PageableDefault(size = 20, sort = "firstName", direction = Sort.Direction.ASC) Pageable pageable) {
        return employeeService.list(new EmployeeFilter(search, departmentId, designationId, locationId, gradeId,
                managerId, status, employmentType, includeExited, teamOnly), pageable);
    }

    @Operation(summary = "Quick search for pickers (e.g. choosing a manager)")
    @GetMapping("/lookup")
    public List<EmployeeLookupDto> lookup(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "10") int limit) {
        return employeeService.lookup(q, limit);
    }

    @Operation(summary = "The signed-in user's own employee profile")
    @GetMapping("/me")
    public EmployeeDetailDto me() {
        Long id = access.selfId()
                .orElseThrow(() -> new ResourceNotFoundException("Your login is not linked to an employee record"));
        return employeeService.get(id);
    }

    @GetMapping("/{id}")
    public EmployeeDetailDto get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @Operation(summary = "Create an employee (optionally provisioning a login)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    public CreateEmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    public EmployeeDetailDto update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @Operation(summary = "Soft-delete an employee (refused while they have reports or hold assets)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE') and hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public void delete(@PathVariable Long id) {
        employeeService.delete(id);
    }

    // ---- Excel ----

    @Operation(summary = "Export the (filtered) directory to Excel")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    public ResponseEntity<Resource> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "false") boolean includeExited,
            @RequestParam(defaultValue = "false") boolean teamOnly) {
        EmployeeFilter filter = new EmployeeFilter(search, departmentId, null, locationId, null, null,
                status, null, includeExited, teamOnly);
        return Downloads.attachment(excelService.export(filter), "employees-" + LocalDate.now() + ".xlsx",
                Downloads.XLSX);
    }

    @Operation(summary = "Download the bulk-import template")
    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    public ResponseEntity<Resource> importTemplate() {
        return Downloads.attachment(excelService.template(), "employee-import-template.xlsx", Downloads.XLSX);
    }

    @Operation(summary = "Bulk import employees from Excel (all-or-nothing; dryRun validates only)")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    public ImportReport importEmployees(@RequestParam("file") MultipartFile file,
                                        @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        return excelService.importEmployees(file.getInputStream(), dryRun);
    }
}
