package com.hrgenius.analytics.service;

import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.org.entity.MasterEntity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** Headcount report: one row per current employee, without compensation or statutory data. */
@Service
public class AnalyticsExportService {

    private final EmployeeRepository employees;

    public AnalyticsExportService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public byte[] headcount(LocalDate asOf) {
        List<Employee> current = employees.findAll().stream().filter(e -> AnalyticsService.isCurrent(e, asOf))
                .sorted(Comparator.comparing(Employee::getEmployeeCode)).toList();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Headcount " + asOf);
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            String[] header = {"Code", "Name", "Department", "Designation", "Location", "Grade", "Employment type",
                    "Status", "Manager", "Date of joining", "Tenure (years)"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
                h.getCell(i).setCellStyle(bold);
            }
            int r = 1;
            for (Employee e : current) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getEmployeeCode());
                row.createCell(1).setCellValue(e.getFullName());
                row.createCell(2).setCellValue(name(e.getDepartment()));
                row.createCell(3).setCellValue(name(e.getDesignation()));
                row.createCell(4).setCellValue(name(e.getLocation()));
                row.createCell(5).setCellValue(name(e.getGrade()));
                row.createCell(6).setCellValue(e.getEmploymentType().name());
                row.createCell(7).setCellValue(e.getStatus().name());
                row.createCell(8).setCellValue(e.getManager() != null ? e.getManager().getFullName() : "");
                row.createCell(9).setCellValue(e.getDateOfJoining().toString());
                row.createCell(10).setCellValue(Math.round(ChronoUnit.DAYS.between(e.getDateOfJoining(), asOf) / 36.525) / 10.0);
            }
            for (int i = 0; i < header.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(2, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String name(MasterEntity m) {
        return m == null ? "" : m.getName();
    }
}
