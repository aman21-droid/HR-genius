package com.hrgenius.employee.controller;

import com.hrgenius.employee.dto.EmployeeDtos.OrgChartNodeDto;
import com.hrgenius.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Org chart")
@RestController
@RequestMapping("/api/v1/org-chart")
public class OrgChartController {

    private final EmployeeService employeeService;

    public OrgChartController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /** Flat node list (with managerId); the client assembles the tree. Current employees only. */
    @Operation(summary = "Org chart nodes for all current employees")
    @GetMapping
    public List<OrgChartNodeDto> nodes() {
        return employeeService.orgChart();
    }
}
