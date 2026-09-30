package com.hrgenius.payroll.controller;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.web.Downloads;
import com.hrgenius.payroll.dto.PayrollDtos.*;
import com.hrgenius.payroll.service.PayrollExportService;
import com.hrgenius.payroll.service.PayrollExportService.Export;
import com.hrgenius.payroll.service.PayrollService;
import com.hrgenius.payroll.service.PayslipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Payroll API. Runs, structure and exports need PAYROLL_RUN; approval happens in the Approvals
 * inbox (PAYROLL_APPROVE). Self-service endpoints ("me") are open to any linked employee.
 */
@Tag(name = "Payroll")
@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private static final String RUN = "hasAuthority('PAYROLL_RUN')";

    private final PayrollService payroll;
    private final PayslipService payslips;
    private final PayrollExportService exports;
    private final CurrentUserService currentUser;

    public PayrollController(PayrollService payroll, PayslipService payslips, PayrollExportService exports,
                             CurrentUserService currentUser) {
        this.payroll = payroll;
        this.payslips = payslips;
        this.exports = exports;
        this.currentUser = currentUser;
    }

    // ---- runs ----

    @Operation(summary = "Payroll runs, newest first")
    @GetMapping("/runs")
    @PreAuthorize(RUN)
    public List<RunDto> runs() {
        return payroll.list();
    }

    @Operation(summary = "One payroll run")
    @GetMapping("/runs/{id}")
    @PreAuthorize(RUN)
    public RunDto run(@PathVariable Long id) {
        return payroll.get(id);
    }

    @Operation(summary = "Create a draft run for a month")
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RUN)
    public RunDto create(@Valid @RequestBody CreateRunRequest req) {
        return payroll.create(req);
    }

    @Operation(summary = "(Re)calculate every payslip of a draft/calculated run")
    @PostMapping("/runs/{id}/calculate")
    @PreAuthorize(RUN)
    public RunDto calculate(@PathVariable Long id) {
        return payroll.calculate(id);
    }

    @Operation(summary = "Submit a calculated run for approval")
    @PostMapping("/runs/{id}/submit")
    @PreAuthorize(RUN)
    public RunDto submit(@PathVariable Long id) {
        return payroll.submit(id, me());
    }

    @Operation(summary = "Mark an approved run as paid")
    @PostMapping("/runs/{id}/paid")
    @PreAuthorize(RUN)
    public RunDto markPaid(@PathVariable Long id, @Valid @RequestBody MarkPaidRequest req) {
        return payroll.markPaid(id, req);
    }

    @Operation(summary = "Delete a draft/calculated run")
    @DeleteMapping("/runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RUN)
    public void delete(@PathVariable Long id) {
        payroll.delete(id);
    }

    @Operation(summary = "Payslips of a run")
    @GetMapping("/runs/{id}/payslips")
    @PreAuthorize(RUN)
    public List<PayslipSummaryDto> runPayslips(@PathVariable Long id) {
        return payroll.payslips(id);
    }

    @Operation(summary = "Payroll register (xlsx)")
    @GetMapping("/runs/{id}/register")
    @PreAuthorize(RUN)
    public ResponseEntity<Resource> register(@PathVariable Long id) {
        Export x = exports.register(id);
        return Downloads.attachment(x.content(), x.fileName(), Downloads.XLSX);
    }

    @Operation(summary = "Bank transfer file with full account numbers (xlsx, audited)")
    @GetMapping("/runs/{id}/bank-file")
    @PreAuthorize(RUN)
    public ResponseEntity<Resource> bankFile(@PathVariable Long id) {
        Export x = exports.bankTransfer(id);
        return Downloads.attachment(x.content(), x.fileName(), Downloads.XLSX);
    }

    // ---- adjustments ----

    @Operation(summary = "One-off adjustments in a run")
    @GetMapping("/runs/{id}/adjustments")
    @PreAuthorize(RUN)
    public List<AdjustmentDto> adjustments(@PathVariable Long id) {
        return payroll.adjustments(id);
    }

    @Operation(summary = "Add a bonus/deduction to a run (returns the run to draft)")
    @PostMapping("/runs/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RUN)
    public AdjustmentDto addAdjustment(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest req) {
        return payroll.addAdjustment(id, req);
    }

    @Operation(summary = "Remove an adjustment (returns the run to draft)")
    @DeleteMapping("/runs/{id}/adjustments/{adjustmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RUN)
    public void removeAdjustment(@PathVariable Long id, @PathVariable Long adjustmentId) {
        payroll.removeAdjustment(id, adjustmentId);
    }

    // ---- salary structure ----

    @Operation(summary = "Salary structure components")
    @GetMapping("/components")
    @PreAuthorize(RUN)
    public List<ComponentDto> components() {
        return payroll.components();
    }

    @Operation(summary = "Add a component")
    @PostMapping("/components")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RUN)
    public ComponentDto createComponent(@Valid @RequestBody ComponentRequest req) {
        return payroll.createComponent(req);
    }

    @Operation(summary = "Edit a component")
    @PutMapping("/components/{id}")
    @PreAuthorize(RUN)
    public ComponentDto updateComponent(@PathVariable Long id, @Valid @RequestBody ComponentRequest req) {
        return payroll.updateComponent(id, req);
    }

    @Operation(summary = "Delete a component")
    @DeleteMapping("/components/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RUN)
    public void deleteComponent(@PathVariable Long id) {
        payroll.deleteComponent(id);
    }

    // ---- payslips (payroll staff or the owner once published) ----

    @Operation(summary = "One payslip")
    @GetMapping("/payslips/{id}")
    public PayslipDto payslip(@PathVariable Long id) {
        return payslips.get(id);
    }

    @Operation(summary = "Payslip PDF")
    @GetMapping("/payslips/{id}/pdf")
    public ResponseEntity<Resource> payslipPdf(@PathVariable Long id) {
        PayslipDto d = payslips.get(id);
        return Downloads.attachment(payslips.pdf(id), "payslip-" + d.period() + "-" + d.employeeCode() + ".pdf",
                MediaType.APPLICATION_PDF);
    }

    // ---- self-service ----

    @Operation(summary = "My published payslips (id = payslip id)")
    @GetMapping("/me/payslips")
    public List<RunDto> myPayslips() {
        return payslips.myPayslipPeriods(me());
    }

    @Operation(summary = "My salary structure from my current CTC")
    @GetMapping("/me/structure")
    public StructureDto myStructure() {
        return payroll.myStructure(me());
    }

    private Long me() {
        return currentUser.employeeId()
                .orElseThrow(() -> new BadRequestException("Your login is not linked to an employee profile"));
    }
}
