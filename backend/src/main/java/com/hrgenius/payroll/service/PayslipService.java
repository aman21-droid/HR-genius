package com.hrgenius.payroll.service;

import com.hrgenius.common.error.ResourceNotFoundException;
import com.hrgenius.common.security.CurrentUserService;
import com.hrgenius.common.util.MaskingUtil;
import com.hrgenius.common.util.MoneyFormat;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeStatutory;
import com.hrgenius.employee.repository.EmployeeStatutoryRepository;
import com.hrgenius.org.dto.OrgDtos.CompanyDto;
import com.hrgenius.org.service.CompanyService;
import com.hrgenius.payroll.calc.PayrollCalculator.LineType;
import com.hrgenius.payroll.dto.PayrollDtos.PayslipDto;
import com.hrgenius.payroll.dto.PayrollDtos.PayslipLineDto;
import com.hrgenius.payroll.dto.PayrollDtos.RunDto;
import com.hrgenius.payroll.entity.PayrollRun;
import com.hrgenius.payroll.entity.PayrollRun.RunStatus;
import com.hrgenius.payroll.entity.Payslip;
import com.hrgenius.payroll.entity.PayslipLine;
import com.hrgenius.payroll.repository.PayslipRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Payslip views and PDFs. Employees see their own payslips once a run is approved; payroll staff
 * (PAYROLL_RUN) see any payslip in any state.
 */
@Service
public class PayslipService {

    static final String RUN_PERMISSION = "PAYROLL_RUN";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final PayslipRepository payslips;
    private final EmployeeStatutoryRepository statutory;
    private final CurrentUserService currentUser;
    private final CompanyService companyService;
    private final AuditService audit;

    public PayslipService(PayslipRepository payslips, EmployeeStatutoryRepository statutory,
                          CurrentUserService currentUser, CompanyService companyService, AuditService audit) {
        this.payslips = payslips;
        this.statutory = statutory;
        this.currentUser = currentUser;
        this.companyService = companyService;
        this.audit = audit;
    }

    /** The signed-in employee's published payslips, newest first. */
    @Transactional(readOnly = true)
    public List<RunDto> myPayslipPeriods(Long employeeId) {
        return payslips.findForEmployee(employeeId, EnumSet.of(RunStatus.APPROVED, RunStatus.PAID)).stream()
                .map(p -> {
                    PayrollRun r = p.getRun();
                    // Re-use RunDto as a compact list row: totals are this employee's own figures.
                    return new RunDto(p.getId(), r.getPeriod(), r.getPeriodStart(), r.getPeriodEnd(), r.getStatus().name(),
                            1, p.getGrossEarnings(), p.getTotalDeductions(), p.getNetPay(),
                            p.getGrossEarnings().add(p.getEmployerPf()).add(p.getEmployerEsi()), null,
                            r.getCalculatedAt(), r.getApprovedAt(), r.getPaidAt(), null, null, 0, p.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PayslipDto get(Long payslipId) {
        Payslip p = findVisible(payslipId);
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long payslipId) {
        Payslip p = findVisible(payslipId);
        PayslipDto d = toDto(p);
        if (!isSelf(p)) {
            audit.record("Payslip", p.getId(), AuditAction.VIEW_SENSITIVE,
                    "Downloaded payslip " + d.period() + " of " + d.employeeCode());
        }
        return render(d);
    }

    // ------------------------------------------------------------------ access

    private Payslip findVisible(Long id) {
        Payslip p = payslips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payslip " + id + " not found"));
        if (currentUser.hasAuthority(RUN_PERMISSION)) {
            return p;
        }
        if (isSelf(p) && p.getRun().isPublished()) {
            return p;
        }
        // Same message for "not yours" and "not yet released" so ids cannot be probed.
        throw new AccessDeniedException("Not allowed to view this payslip");
    }

    private boolean isSelf(Payslip p) {
        Long me = currentUser.employeeId().orElse(null);
        return me != null && me.equals(p.getEmployee().getId());
    }

    // ------------------------------------------------------------------ mapping

    PayslipDto toDto(Payslip p) {
        Employee e = p.getEmployee();
        PayrollRun r = p.getRun();
        EmployeeStatutory st = statutory.findById(e.getId()).orElse(null);
        List<PayslipLineDto> earnings = lines(p, LineType.EARNING);
        List<PayslipLineDto> deductions = lines(p, LineType.DEDUCTION);
        List<PayslipLineDto> employer = lines(p, LineType.EMPLOYER);
        return new PayslipDto(p.getId(), r.getId(), r.getPeriod(), r.getStatus().name(), e.getId(), e.getEmployeeCode(),
                e.getFullName(), nameOf(e.getDesignation()), nameOf(e.getDepartment()), nameOf(e.getLocation()),
                e.getDateOfJoining(),
                st != null && st.getPan() != null ? MaskingUtil.mask(st.getPan(), 4) : null,
                st != null ? st.getUan() : null,
                p.getDaysInPeriod(), p.getLopDays(), p.getPaidDays(), p.getAnnualCtc(), p.getGrossEarnings(),
                p.getTotalDeductions(), p.getNetPay(), p.getEmployerPf(), p.getEmployerEsi(), p.getTaxRegime(),
                p.getBankName(), p.getAccountMasked(), p.getBankIfsc(), earnings, deductions, employer);
    }

    private static List<PayslipLineDto> lines(Payslip p, LineType type) {
        return p.getLines().stream().filter(l -> l.getLineType() == type)
                .map(PayslipService::line).toList();
    }

    private static PayslipLineDto line(PayslipLine l) {
        return new PayslipLineDto(l.getCode(), l.getName(), l.getLineType().name(), l.getAmount());
    }

    private static String nameOf(com.hrgenius.org.entity.MasterEntity m) {
        return m == null ? null : m.getName();
    }

    // ------------------------------------------------------------------ PDF

    private byte[] render(PayslipDto d) {
        CompanyDto company = companyService.get();
        String companyName = Optional.ofNullable(company.legalName()).filter(s -> !s.isBlank()).orElse(company.name());
        java.time.YearMonth ym = java.time.YearMonth.parse(d.period());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 44, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        doc.add(new Paragraph(companyName, title));
        doc.add(new Paragraph("Payslip for " + MONTH.format(ym.atDay(1)), h));
        doc.add(Chunk.NEWLINE);

        PdfPTable info = new PdfPTable(4);
        info.setWidthPercentage(100);
        info.setWidths(new float[]{1.2f, 2f, 1.2f, 2f});
        cell(info, "Employee", h); cell(info, d.employeeName() + " (" + d.employeeCode() + ")", body);
        cell(info, "Designation", h); cell(info, nz(d.designation()), body);
        cell(info, "Department", h); cell(info, nz(d.department()), body);
        cell(info, "Location", h); cell(info, nz(d.location()), body);
        cell(info, "Date of joining", h); cell(info, d.dateOfJoining() == null ? "—" : DAY.format(d.dateOfJoining()), body);
        cell(info, "PAN", h); cell(info, nz(d.pan()), body);
        cell(info, "UAN", h); cell(info, nz(d.uan()), body);
        cell(info, "Bank", h); cell(info, d.bankName() == null ? "—" : d.bankName() + " " + nz(d.accountMasked()), body);
        cell(info, "Paid days", h); cell(info, plain(d.paidDays()) + " of " + d.daysInPeriod(), body);
        cell(info, "Loss of pay", h); cell(info, plain(d.lopDays()) + " day(s)", body);
        doc.add(info);
        doc.add(Chunk.NEWLINE);

        PdfPTable pay = new PdfPTable(4);
        pay.setWidthPercentage(100);
        pay.setWidths(new float[]{2.4f, 1.2f, 2.4f, 1.2f});
        header(pay, "Earnings", h); header(pay, "Amount (INR)", h); header(pay, "Deductions", h); header(pay, "Amount (INR)", h);
        int rows = Math.max(d.earnings().size(), d.deductions().size());
        for (int i = 0; i < rows; i++) {
            PayslipLineDto e = i < d.earnings().size() ? d.earnings().get(i) : null;
            PayslipLineDto x = i < d.deductions().size() ? d.deductions().get(i) : null;
            cell(pay, e == null ? "" : e.name(), body); amount(pay, e == null ? null : e.amount(), body);
            cell(pay, x == null ? "" : x.name(), body); amount(pay, x == null ? null : x.amount(), body);
        }
        cell(pay, "Gross earnings", h); amount(pay, d.grossEarnings(), h);
        cell(pay, "Total deductions", h); amount(pay, d.totalDeductions(), h);
        doc.add(pay);
        doc.add(Chunk.NEWLINE);

        Paragraph net = new Paragraph("Net pay: INR " + money(d.netPay()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        doc.add(net);
        if (!d.employer().isEmpty()) {
            StringBuilder sb = new StringBuilder("Employer contributions (not part of net pay): ");
            for (int i = 0; i < d.employer().size(); i++) {
                sb.append(i > 0 ? ", " : "").append(d.employer().get(i).name()).append(" INR ").append(money(d.employer().get(i).amount()));
            }
            doc.add(new Paragraph(sb.toString(), small));
        }
        doc.add(new Paragraph("Tax regime: " + nz(d.taxRegime()).toLowerCase() + ". TDS is an estimate based on your "
                + "current salary structure. This is a computer-generated payslip and needs no signature.", small));
        doc.close();
        return out.toByteArray();
    }

    private static void cell(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(4);
        c.setBorderColor(new Color(220, 220, 220));
        t.addCell(c);
    }

    private static void header(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(4);
        c.setBackgroundColor(new Color(240, 243, 248));
        c.setBorderColor(new Color(220, 220, 220));
        t.addCell(c);
    }

    private static void amount(PdfPTable t, BigDecimal v, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : money(v), f));
        c.setPadding(4);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setBorderColor(new Color(220, 220, 220));
        t.addCell(c);
    }

    static String money(BigDecimal v) {
        return MoneyFormat.grouped(v);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
