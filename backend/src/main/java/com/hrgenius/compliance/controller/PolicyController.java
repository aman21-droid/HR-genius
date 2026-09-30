package com.hrgenius.compliance.controller;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.compliance.dto.PolicyDtos.*;
import com.hrgenius.compliance.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Policy library: everyone reads and acknowledges; POLICY_MANAGE authors and tracks compliance. */
@Tag(name = "Policies")
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private static final String MANAGE = "hasAuthority('POLICY_MANAGE')";

    private final PolicyService policies;
    private final CurrentUserService currentUser;

    public PolicyController(PolicyService policies, CurrentUserService currentUser) {
        this.policies = policies;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Policies (published for everyone; HR also sees drafts and ack counts)")
    @GetMapping
    public List<PolicyDto> list() {
        return policies.list(currentUser.employeeId().orElse(null));
    }

    @Operation(summary = "Policies I still need to acknowledge")
    @GetMapping("/pending")
    public List<PolicyDto> pending() {
        return policies.pendingFor(me());
    }

    @Operation(summary = "Acknowledge the current version of a policy")
    @PostMapping("/{id}/acknowledge")
    public PolicyDto acknowledge(@PathVariable Long id) {
        return policies.acknowledge(id, me());
    }

    @Operation(summary = "Draft a policy")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public PolicyDto create(@Valid @RequestBody PolicyRequest req) {
        return policies.create(req);
    }

    @Operation(summary = "Edit a policy (editing a published one creates a new version)")
    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public PolicyDto update(@PathVariable Long id, @Valid @RequestBody PolicyRequest req) {
        return policies.update(id, req);
    }

    @Operation(summary = "Publish a draft policy")
    @PostMapping("/{id}/publish")
    @PreAuthorize(MANAGE)
    public PolicyDto publish(@PathVariable Long id) {
        return policies.publish(id);
    }

    @Operation(summary = "Archive a policy")
    @PostMapping("/{id}/archive")
    @PreAuthorize(MANAGE)
    public PolicyDto archive(@PathVariable Long id) {
        return policies.archive(id);
    }

    @Operation(summary = "Acknowledgement status for the current version")
    @GetMapping("/{id}/compliance")
    @PreAuthorize(MANAGE)
    public ComplianceDto compliance(@PathVariable Long id) {
        return policies.compliance(id);
    }

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
