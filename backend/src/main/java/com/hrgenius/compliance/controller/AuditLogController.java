package com.hrgenius.compliance.controller;

import com.hrgenius.common.dto.PageResponse;
import com.hrgenius.compliance.dto.AuditLogDto;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Audit trail")
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "Search the audit trail (who changed what, when, old -> new)")
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditLogDto> search(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(entity, entityId, actor, action, from, to, pageable);
    }
}
