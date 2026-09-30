package com.hrgenius.org.controller;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.org.dto.OrgDtos.*;
import com.hrgenius.org.service.CompanyService;
import com.hrgenius.org.service.MasterType;
import com.hrgenius.org.service.OrgMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Company profile and org masters. Literal paths (/company, /lookups) take precedence over
 * the generic /{type} template in Spring MVC's matching.
 */
@Tag(name = "Organization")
@RestController
@RequestMapping("/api/v1/org")
public class OrgController {

    private final OrgMasterService masterService;
    private final CompanyService companyService;

    public OrgController(OrgMasterService masterService, CompanyService companyService) {
        this.masterService = masterService;
        this.companyService = companyService;
    }

    // ---- company ----

    @Operation(summary = "Get the company profile")
    @GetMapping("/company")
    public CompanyDto company() {
        return companyService.get();
    }

    @Operation(summary = "Update the company profile")
    @PutMapping("/company")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public CompanyDto updateCompany(@Valid @RequestBody CompanyRequest request) {
        return companyService.update(request);
    }

    // ---- lookups ----

    @Operation(summary = "All active masters in one call, for form dropdowns (cached)")
    @GetMapping("/lookups")
    public OrgLookups lookups() {
        return masterService.lookups();
    }

    // ---- generic masters ----

    @Operation(summary = "List masters of a type (business-units, cost-centers, departments, designations, grades, locations)")
    @GetMapping("/{type}")
    public PageResponse<MasterDto> list(
            @Parameter(example = "departments") @PathVariable String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return masterService.list(MasterType.fromSlug(type), search, active, pageable);
    }

    @GetMapping("/{type}/{id}")
    public MasterDto get(@PathVariable String type, @PathVariable Long id) {
        return masterService.get(MasterType.fromSlug(type), id);
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public MasterDto create(@PathVariable String type, @Valid @RequestBody MasterRequest request) {
        return masterService.create(MasterType.fromSlug(type), request);
    }

    @PutMapping("/{type}/{id}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public MasterDto update(@PathVariable String type, @PathVariable Long id,
                            @Valid @RequestBody MasterRequest request) {
        return masterService.update(MasterType.fromSlug(type), id, request);
    }

    @Operation(summary = "Soft-delete a master (refused while current employees reference it)")
    @DeleteMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public void delete(@PathVariable String type, @PathVariable Long id) {
        masterService.delete(MasterType.fromSlug(type), id);
    }
}
