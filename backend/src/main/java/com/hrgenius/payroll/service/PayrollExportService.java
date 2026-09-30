package com.hrgenius.payroll.service;

import com.hrgenius.common.error.BusinessException;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeStatutory;
import com.hrgenius.employee.repository.EmployeeStatutoryRepository;
import com.hrgenius.payroll.calc.PayrollCalculator.LineType;
import com.hrgenius.payroll.entity.PayrollRun;
import com.hrgenius.payroll.entity.PayrollRun.RunStatus;
import com.hrgenius.payroll.entity.Payslip;
import com.hrgenius.payroll.entity.PayslipLine;
import com.hrgenius.payroll.repository.PayslipRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.*;

/** Excel outputs for a run: the payroll register and the bank-transfer instruction file. */
@Service
public class PayrollExportService {

    private final PayrollService payrollService;
    private final PayslipRepository payslips;
    private final EmployeeStatutoryRepository statutory;
    private final AuditService audit;

    public PayrollExportService(PayrollService payrollService, PayslipRepository payslips,
                                EmployeeStatutoryRepository statutory, AuditService audit) {
        this.payrollService = payrollService;
        this.payslips = payslips;
        this.statutory = statutory;
        this.audit = audit;
    }

    /** One row per employee with a column per earning/deduction code seen in the run. */
    @Transactional(readOnly = true)
    public Export register(Long runId) {
        PayrollRun run = payrollService.find(runId);
        List<Payslip> slips = payslips.findByRun(runId);
        Map<String, String> earnings = new LinkedHashMap<>();
        Map<String, String> deductions = new LinkedHashMap<>();
        for (Payslip p : slips) {
            for (PayslipLine l : p.getLines()) {
                if (l.getLineType() == LineType.EARNING) {
                    earnings.putIfAbsent(l.getCode(), l.getName());
                } else if (l.getLineType() == LineType.DEDUCTION) {
                    deductions.putIfAbsent(l.getCode(), l.getName());
                }
            }
        }
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Register " + run.getPeriod());
            CellStyle bold = boldStyle(wb);
            CellStyle money = moneyStyle(wb);
            List<String> header = new ArrayList<>(List.of("Code", "Name", "Department", "Paid days", "LOP days"));
            header.addAll(earnings.values());
            header.add("Gross");
            header.addAll(deductions.values());
            header.addAll(List.of("Total deductions", "Net pay", "Employer PF", "Employer ESI"));
            writeHeader(sheet, header, bold);

            int r = 1;
            for (Payslip p : slips) {
                Map<String, BigDecimal> amounts = new HashMap<>();
                p.getLines().forEach(l -> amounts.merge(l.getCode(), l.getAmount(), BigDecimal::add));
                Row row = sheet.createRow(r++);
                Employee e = p.getEmployee();
                int c = 0;
                row.createCell(c++).setCellValue(e.getEmployeeCode());
                row.createCell(c++).setCellValue(e.getFullName());
                row.createCell(c++).setCellValue(e.getDepartment() != null ? e.getDepartment().getName() : "");
                row.createCell(c++).setCellValue(p.getPaidDays().doubleValue());
                row.createCell(c++).setCellValue(p.getLopDays().doubleValue());
                for (String code : earnings.keySet()) {
                    num(row, c++, amounts.getOrDefault(code, BigDecimal.ZERO), money);
                }
                num(row, c++, p.getGrossEarnings(), money);
                for (String code : deductions.keySet()) {
                    num(row, c++, amounts.getOrDefault(code, BigDecimal.ZERO), money);
                }
                num(row, c++, p.getTotalDeductions(), money);
                num(row, c++, p.getNetPay(), money);
                num(row, c++, p.getEmployerPf(), money);
                num(row, c++, p.getEmployerEsi(), money);
            }
            Row total = sheet.createRow(r);
            total.createCell(1).setCellValue("Total (" + slips.size() + ")");
            total.getCell(1).setCellStyle(bold);
            int grossCol = 5 + earnings.size();
            num(total, grossCol, run.getTotalGross(), money);
            num(total, grossCol + deductions.size() + 1, run.getTotalDeductions(), money);
            num(total, grossCol + deductions.size() + 2, run.getTotalNet(), money);
            for (int i = 0; i < header.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(2, 1);
            wb.write(out);
            return new Export(out.toByteArray(), "payroll-register-" + run.getPeriod() + ".xlsx");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Bank transfer instructions with full (decrypted) account numbers. Only for approved/paid runs,
     * and every download is written to the audit trail.
     */
    @Transactional
    public Export bankTransfer(Long runId) {
        PayrollRun run = payrollService.find(runId);
        if (run.getStatus() != RunStatus.APPROVED && run.getStatus() != RunStatus.PAID) {
            throw new BusinessException("The bank file is available once the run is approved");
        }
        List<Payslip> slips = payslips.findByRun(runId);
        int missing = 0;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Transfers " + run.getPeriod());
            CellStyle bold = boldStyle(wb);
            CellStyle money = moneyStyle(wb);
            writeHeader(sheet, List.of("Employee code", "Beneficiary name", "Account number", "IFSC", "Bank", "Amount (INR)",
                    "Narration"), bold);
            int r = 1;
            for (Payslip p : slips) {
                if (p.getNetPay().signum() <= 0) {
                    continue;
                }
                Employee e = p.getEmployee();
                EmployeeStatutory st = statutory.findById(e.getId()).orElse(null);
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getEmployeeCode());
                row.createCell(1).setCellValue(st != null && st.getAccountHolderName() != null ? st.getAccountHolderName() : e.getFullName());
                String account = st != null ? st.getBankAccountNumber() : null;
                row.createCell(2).setCellValue(account != null ? account : "MISSING");
                row.createCell(3).setCellValue(st != null && st.getBankIfsc() != null ? st.getBankIfsc() : "MISSING");
                row.createCell(4).setCellValue(st != null && st.getBankName() != null ? st.getBankName() : "");
                num(row, 5, p.getNetPay(), money);
                row.createCell(6).setCellValue("Salary " + run.getPeriod());
                if (account == null || st.getBankIfsc() == null) {
                    missing++;
                }
            }
            for (int i = 0; i < 7; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            audit.record("PayrollRun", run.getId(), AuditAction.VIEW_SENSITIVE,
                    "Exported bank transfer file for " + run.getPeriod() + " (" + (r - 1) + " rows"
                            + (missing > 0 ? ", " + missing + " missing bank details" : "") + ")");
            return new Export(out.toByteArray(), "bank-transfer-" + run.getPeriod() + ".xlsx");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void writeHeader(Sheet sheet, List<String> header, CellStyle bold) {
        Row h = sheet.createRow(0);
        for (int i = 0; i < header.size(); i++) {
            Cell cell = h.createCell(i);
            cell.setCellValue(header.get(i));
            cell.setCellStyle(bold);
        }
    }

    private static void num(Row row, int col, BigDecimal v, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(v == null ? 0 : v.doubleValue());
        cell.setCellStyle(style);
    }

    private static CellStyle boldStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private static CellStyle moneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        return s;
    }

    public record Export(byte[] content, String fileName) {
    }
}
