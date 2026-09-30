package com.hrgenius.employee.service;

import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.util.MaskingUtil;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import com.hrgenius.employee.dto.ProfileDtos.StatutoryDto;
import com.hrgenius.employee.dto.ProfileDtos.StatutoryRequest;
import com.hrgenius.employee.entity.EmployeeStatutory;
import com.hrgenius.employee.repository.EmployeeStatutoryRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bank & statutory identifiers. Always returned masked unless {@code reveal=true} is requested by
 * someone entitled to it; every reveal is written to the audit trail. Updates are PATCH-style
 * (null = unchanged) so a form showing masked values can never overwrite real data with them.
 */
@Service
public class StatutoryService {

    private static final String AUDIT_ENTITY = "EmployeeStatutory";

    private final EmployeeStatutoryRepository repository;
    private final EmployeeService employeeService;
    private final EmployeeAccessService access;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public StatutoryService(EmployeeStatutoryRepository repository, EmployeeService employeeService,
                            EmployeeAccessService access, CurrentUserService currentUser, AuditService audit) {
        this.repository = repository;
        this.employeeService = employeeService;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public StatutoryDto get(Long employeeId, boolean reveal) {
        employeeService.find(employeeId);
        access.requireSensitive(employeeId);
        EmployeeStatutory s = repository.findById(employeeId).orElseGet(() -> empty(employeeId));
        if (reveal) {
            audit.record(AUDIT_ENTITY, employeeId, AuditAction.VIEW_SENSITIVE, "Revealed bank & statutory IDs");
        }
        return toDto(s, !reveal);
    }

    @Transactional
    public StatutoryDto update(Long employeeId, StatutoryRequest req) {
        employeeService.find(employeeId);
        if (!canEdit(employeeId)) {
            throw new AccessDeniedException("Not allowed to change bank or statutory details");
        }
        EmployeeStatutory s = repository.findById(employeeId).orElseGet(() -> empty(employeeId));

        // Sensitive values: audit with masked old/new only.
        patchSensitive("pan", s.getPan(), req.pan(), s::setPan, employeeId);
        patchSensitive("aadhaar", s.getAadhaar(), req.aadhaar(), s::setAadhaar, employeeId);
        patchSensitive("bankAccountNumber", s.getBankAccountNumber(), req.bankAccountNumber(),
                s::setBankAccountNumber, employeeId);
        // Non-secret values: audit in clear.
        patchPlain("uan", s.getUan(), req.uan(), s::setUan, employeeId);
        patchPlain("esiNumber", s.getEsiNumber(), req.esiNumber(), s::setEsiNumber, employeeId);
        patchPlain("bankName", s.getBankName(), req.bankName(), s::setBankName, employeeId);
        patchPlain("accountHolderName", s.getAccountHolderName(), req.accountHolderName(),
                s::setAccountHolderName, employeeId);
        patchPlain("bankIfsc", s.getBankIfsc(), req.bankIfsc(), s::setBankIfsc, employeeId);
        if (req.taxRegime() != null && req.taxRegime() != s.getTaxRegime()) {
            audit.recordChange(AUDIT_ENTITY, employeeId, "taxRegime", s.getTaxRegime(), req.taxRegime());
            s.setTaxRegime(req.taxRegime());
        }
        repository.save(s);
        return toDto(s, true);
    }

    /** Needs sensitive access plus either HR edit rights or payroll rights. */
    private boolean canEdit(Long employeeId) {
        return currentUser.hasAuthority("EMPLOYEE_SENSITIVE_READ")
                && (currentUser.hasAuthority("EMPLOYEE_WRITE") || currentUser.hasAuthority("PAYROLL_RUN"))
                && access.canViewSensitive(employeeId);
    }

    private void patchSensitive(String field, String current, String incoming, Consumer<String> setter, Long id) {
        if (incoming != null && !incoming.equals(current)) {
            audit.recordChange(AUDIT_ENTITY, id, field, MaskingUtil.mask(current), MaskingUtil.mask(incoming));
            setter.accept(incoming);
        }
    }

    private void patchPlain(String field, String current, String incoming, Consumer<String> setter, Long id) {
        if (incoming != null && !Objects.equals(incoming, current)) {
            audit.recordChange(AUDIT_ENTITY, id, field, current, incoming);
            setter.accept(incoming);
        }
    }

    private static EmployeeStatutory empty(Long employeeId) {
        EmployeeStatutory s = new EmployeeStatutory();
        s.setEmployeeId(employeeId);
        return s;
    }

    private static StatutoryDto toDto(EmployeeStatutory s, boolean masked) {
        return new StatutoryDto(s.getEmployeeId(),
                masked ? MaskingUtil.mask(s.getPan()) : s.getPan(),
                masked ? MaskingUtil.mask(s.getAadhaar()) : s.getAadhaar(),
                s.getUan(), s.getEsiNumber(), s.getBankName(), s.getAccountHolderName(),
                masked ? MaskingUtil.mask(s.getBankAccountNumber()) : s.getBankAccountNumber(),
                s.getBankIfsc(), s.getTaxRegime() == null ? null : s.getTaxRegime().name(), masked);
    }
}
