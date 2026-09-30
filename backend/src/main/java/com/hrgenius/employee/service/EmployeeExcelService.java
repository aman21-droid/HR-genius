package com.hrgenius.employee.service;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.compliance.entity.AuditLog.AuditAction;
import com.hrgenius.compliance.service.AuditService;
import com.hrgenius.employee.dto.EmployeeDtos.EmployeeRequest;
import com.hrgenius.employee.dto.EmployeeFilter;
import com.hrgenius.employee.dto.ImportDtos.ImportError;
import com.hrgenius.employee.dto.ImportDtos.ImportReport;
import com.hrgenius.employee.entity.Employee;
import com.hrgenius.employee.entity.EmployeeEnums.*;
import com.hrgenius.employee.repository.EmployeeRepository;
import com.hrgenius.org.entity.MasterEntity;
import com.hrgenius.org.repository.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Excel export, import template, and validated bulk import of employees.
 *
 * <p>Import is all-or-nothing: every row is validated first (Bean Validation, org lookups,
 * duplicates within the file and against the database, manager references), and nothing is
 * written unless every row is clean. Rows are then created through {@link EmployeeService},
 * so imported employees get exactly the same codes, rules, timeline and audit as the UI path.
 */
@Slf4j
@Service
public class EmployeeExcelService {

    public static final int MAX_IMPORT_ROWS = 1000;
    public static final int MAX_EXPORT_ROWS = 10_000;

    // Import columns (header label -> required?). Order is the template order.
    static final String C_FIRST = "First Name", C_MIDDLE = "Middle Name", C_LAST = "Last Name",
            C_EMAIL = "Work Email", C_PERSONAL = "Personal Email", C_PHONE = "Phone", C_GENDER = "Gender",
            C_DOB = "Date of Birth", C_DEPT = "Department Code", C_DESIG = "Designation Code",
            C_GRADE = "Grade Code", C_LOC = "Location Code", C_MANAGER = "Manager (Code or Email)",
            C_TYPE = "Employment Type", C_DOJ = "Date of Joining", C_CTC = "Annual CTC";
    static final List<String> COLUMNS = List.of(C_FIRST, C_MIDDLE, C_LAST, C_EMAIL, C_PERSONAL, C_PHONE, C_GENDER,
            C_DOB, C_DEPT, C_DESIG, C_GRADE, C_LOC, C_MANAGER, C_TYPE, C_DOJ, C_CTC);
    static final Set<String> REQUIRED = Set.of(C_FIRST, C_LAST, C_EMAIL, C_DEPT, C_DESIG, C_LOC, C_TYPE, C_DOJ);

    /** Bean Validation property -> column label, for readable error reports. */
    private static final Map<String, String> PROPERTY_COLUMNS = Map.ofEntries(
            Map.entry("firstName", C_FIRST), Map.entry("middleName", C_MIDDLE), Map.entry("lastName", C_LAST),
            Map.entry("workEmail", C_EMAIL), Map.entry("personalEmail", C_PERSONAL), Map.entry("phone", C_PHONE),
            Map.entry("dateOfBirth", C_DOB), Map.entry("departmentId", C_DEPT), Map.entry("designationId", C_DESIG),
            Map.entry("locationId", C_LOC), Map.entry("employmentType", C_TYPE), Map.entry("dateOfJoining", C_DOJ),
            Map.entry("annualCtc", C_CTC));

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    private final EmployeeService employeeService;
    private final EmployeeAccessService access;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final LocationRepository locations;
    private final Validator validator;
    private final AuditService audit;

    public EmployeeExcelService(EmployeeService employeeService, EmployeeAccessService access,
                                EmployeeRepository employeeRepository, DepartmentRepository departments,
                                DesignationRepository designations, GradeRepository grades,
                                LocationRepository locations, Validator validator, AuditService audit) {
        this.employeeService = employeeService;
        this.access = access;
        this.employeeRepository = employeeRepository;
        this.departments = departments;
        this.designations = designations;
        this.grades = grades;
        this.locations = locations;
        this.validator = validator;
        this.audit = audit;
    }

    // =================================================================== export

    @Transactional(readOnly = true)
    public byte[] export(EmployeeFilter filter) {
        boolean full = access.hasFullAccess();
        boolean ctc = full && access.canViewSensitive(null);
        List<Employee> rows = employeeService.findForExport(filter, MAX_EXPORT_ROWS);

        List<String> headers = new ArrayList<>(List.of("Employee Code", "First Name", "Last Name", "Work Email",
                "Phone", "Department", "Designation", "Grade", "Location", "Manager Code", "Manager Name",
                "Employment Type", "Status", "Date of Joining"));
        if (full) headers.addAll(List.of("Gender", "Date of Birth", "Personal Email"));
        if (ctc) headers.add("Annual CTC");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Employees");
            CellStyle headerStyle = headerStyle(wb);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }
            int r = 1;
            for (Employee e : rows) {
                Row row = sheet.createRow(r++);
                int c = 0;
                c = text(row, c, e.getEmployeeCode());
                c = text(row, c, e.getFirstName());
                c = text(row, c, e.getLastName());
                c = text(row, c, e.getWorkEmail());
                c = text(row, c, e.getPhone());
                c = text(row, c, name(e.getDepartment()));
                c = text(row, c, name(e.getDesignation()));
                c = text(row, c, e.getGrade() == null ? null : e.getGrade().getCode());
                c = text(row, c, name(e.getLocation()));
                c = text(row, c, e.getManager() == null ? null : e.getManager().getEmployeeCode());
                c = text(row, c, e.getManager() == null ? null : e.getManager().getFullName());
                c = text(row, c, e.getEmploymentType().name());
                c = text(row, c, e.getStatus().name());
                c = date(row, c, e.getDateOfJoining(), dateStyle);
                if (full) {
                    c = text(row, c, e.getGender() == null ? null : e.getGender().name());
                    c = date(row, c, e.getDateOfBirth(), dateStyle);
                    c = text(row, c, e.getPersonalEmail());
                }
                if (ctc && e.getAnnualCtc() != null) {
                    row.createCell(c).setCellValue(e.getAnnualCtc().doubleValue());
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, r - 1), 0, headers.size() - 1));
            return toBytes(wb);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // =================================================================== template

    @Transactional(readOnly = true)
    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            Sheet sheet = wb.createSheet("Employees");
            Row h = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(REQUIRED.contains(COLUMNS.get(i)) ? COLUMNS.get(i) + " *" : COLUMNS.get(i));
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            // One example row the user can overwrite.
            Row ex = sheet.createRow(1);
            String[] sample = {"Asha", "", "Menon", "asha.menon@hrgenius.com", "", "+91 9876543210", "FEMALE",
                    "1995-08-14", firstCode(departments), firstCode(designations), firstCode(grades),
                    firstCode(locations), "", "FULL_TIME", LocalDate.now().plusDays(14).toString(), "1200000"};
            for (int i = 0; i < sample.length; i++) {
                ex.createCell(i).setCellValue(sample[i]);
            }
            sheet.createFreezePane(0, 1);
            addDropdown(sheet, COLUMNS.indexOf(C_GENDER), names(Gender.values()));
            addDropdown(sheet, COLUMNS.indexOf(C_TYPE), names(EmploymentType.values()));

            // Reference sheet with valid codes.
            Sheet ref = wb.createSheet("Valid Codes");
            String[] refHeaders = {"Department Code", "Department", "Designation Code", "Designation",
                    "Grade Code", "Grade", "Location Code", "Location"};
            Row rh = ref.createRow(0);
            for (int i = 0; i < refHeaders.length; i++) {
                Cell c = rh.createCell(i);
                c.setCellValue(refHeaders[i]);
                c.setCellStyle(headerStyle);
                ref.setColumnWidth(i, 24 * 256);
            }
            writeCodes(ref, 0, departments.findAllByActiveTrueOrderByNameAsc());
            writeCodes(ref, 2, designations.findAllByActiveTrueOrderByNameAsc());
            writeCodes(ref, 4, grades.findAllByActiveTrueOrderByNameAsc());
            writeCodes(ref, 6, locations.findAllByActiveTrueOrderByNameAsc());

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "How to import employees",
                    "1. Fill one employee per row on the 'Employees' sheet (delete the example row).",
                    "2. Columns marked * are required.",
                    "3. Use codes from the 'Valid Codes' sheet for department, designation, grade and location.",
                    "4. Dates: YYYY-MM-DD (or DD/MM/YYYY). Excel date cells also work.",
                    "5. Manager: an existing employee's code (e.g. EMP0009) or work email, or the work email "
                            + "of another row in this file.",
                    "6. Employment Type: FULL_TIME, PART_TIME, CONTRACT or INTERN.",
                    "7. Upload with 'Validate only' first. Nothing is saved unless every row is valid.",
                    "Maximum " + MAX_IMPORT_ROWS + " rows per file."};
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.setColumnWidth(0, 110 * 256);
            return toBytes(wb);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // =================================================================== import

    @Transactional
    public ImportReport importEmployees(InputStream in, boolean dryRun) {
        List<ParsedRow> rows;
        try (Workbook wb = WorkbookFactory.create(in)) {
            rows = parse(wb.getSheetAt(0));
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BadRequestException bre) throw bre;
            throw new BadRequestException("Could not read the file. Upload an .xlsx created from the template.");
        }

        List<ImportError> errors = new ArrayList<>();
        Lookups lookups = loadLookups();
        List<ValidRow> valid = new ArrayList<>();
        Map<String, Integer> emailsInFile = new HashMap<>();

        for (ParsedRow row : rows) {
            int before = errors.size();
            ValidRow v = validateRow(row, lookups, emailsInFile, errors);
            if (v != null && errors.size() == before) {
                valid.add(v);
            }
        }
        resolveManagers(valid, emailsInFile, errors);

        if (!errors.isEmpty() || dryRun) {
            errors.sort(Comparator.comparingInt(ImportError::row));
            return new ImportReport(rows.size(), errors.isEmpty() ? valid.size() : countValid(rows, errors),
                    0, dryRun, errors);
        }

        // Pass 1: create everyone (with managers that already exist in the DB).
        Map<String, Long> createdIds = new HashMap<>();
        for (ValidRow v : valid) {
            var resp = employeeService.create(v.request());
            createdIds.put(v.request().workEmail().toLowerCase(), resp.employee().id());
        }
        // Pass 2: link managers that were defined in this same file.
        for (ValidRow v : valid) {
            if (v.inFileManagerEmail() != null) {
                employeeService.assignManager(createdIds.get(v.request().workEmail().toLowerCase()),
                        createdIds.get(v.inFileManagerEmail()));
            }
        }
        audit.record("Employee", null, AuditAction.IMPORT, "Imported " + valid.size() + " employee(s) from Excel");
        log.info("Imported {} employees", valid.size());
        return new ImportReport(rows.size(), valid.size(), valid.size(), false, List.of());
    }

    private List<ParsedRow> parse(Sheet sheet) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            throw new BadRequestException("The sheet is empty");
        }
        DataFormatter fmt = new DataFormatter();
        Map<String, Integer> index = new HashMap<>();
        for (Cell c : header) {
            String label = fmt.formatCellValue(c).replace("*", "").trim();
            COLUMNS.stream().filter(col -> col.equalsIgnoreCase(label)).findFirst()
                    .ifPresent(col -> index.put(col, c.getColumnIndex()));
        }
        List<String> missing = REQUIRED.stream().filter(col -> !index.containsKey(col)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required column(s): " + String.join(", ", missing)
                    + ". Download the template to see the expected headers.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Cell> cells = new HashMap<>();
            boolean blank = true;
            for (var entry : index.entrySet()) {
                Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null && !fmt.formatCellValue(cell).isBlank()) {
                    cells.put(entry.getKey(), cell);
                    blank = false;
                }
            }
            if (blank) continue;
            if (rows.size() >= MAX_IMPORT_ROWS) {
                throw new BadRequestException("Too many rows: the limit is " + MAX_IMPORT_ROWS + " per file");
            }
            rows.add(new ParsedRow(r + 1, cells));
        }
        if (rows.isEmpty()) {
            throw new BadRequestException("No employee rows found in the file");
        }
        return rows;
    }

    private ValidRow validateRow(ParsedRow row, Lookups lk, Map<String, Integer> emailsInFile,
                                 List<ImportError> errors) {
        int n = row.rowNumber();
        String email = row.text(C_EMAIL);
        String emailKey = email == null ? null : email.trim().toLowerCase();
        if (emailKey != null) {
            Integer firstRow = emailsInFile.putIfAbsent(emailKey, n);
            if (firstRow != null) {
                errors.add(new ImportError(n, C_EMAIL, "Duplicate of row " + firstRow + " in this file"));
            } else if (employeeRepository.existsByWorkEmailIgnoreCase(emailKey)) {
                errors.add(new ImportError(n, C_EMAIL, "An employee with this email already exists"));
            }
        }

        Long dept = code(row, C_DEPT, lk.departments(), n, errors);
        Long desig = code(row, C_DESIG, lk.designations(), n, errors);
        Long grade = code(row, C_GRADE, lk.grades(), n, errors);
        Long loc = code(row, C_LOC, lk.locations(), n, errors);
        Gender gender = enumValue(row, C_GENDER, Gender.class, n, errors);
        EmploymentType type = enumValue(row, C_TYPE, EmploymentType.class, n, errors);
        LocalDate dob = row.date(C_DOB, n, errors);
        LocalDate doj = row.date(C_DOJ, n, errors);
        BigDecimal ctc = row.decimal(C_CTC, n, errors);

        EmployeeRequest req = new EmployeeRequest(row.text(C_FIRST), row.text(C_MIDDLE), row.text(C_LAST),
                emailKey, row.text(C_PERSONAL), row.text(C_PHONE), gender, dob, null, null, null, null, null,
                dept, desig, grade, loc, null, type, null, doj, null, null, ctc, false);

        for (ConstraintViolation<EmployeeRequest> v : validator.validate(req)) {
            String prop = v.getPropertyPath().toString();
            String col = PROPERTY_COLUMNS.getOrDefault(prop, prop);
            // Null ids/enums already reported as "not found"/"invalid" above; avoid duplicates.
            boolean alreadyReported = errors.stream().anyMatch(e -> e.row() == n && e.column().equals(col));
            if (!alreadyReported) {
                errors.add(new ImportError(n, col, REQUIRED.contains(col) && v.getInvalidValue() == null
                        ? "Required" : v.getMessage()));
            }
        }
        if (dob != null && doj != null && dob.plusYears(16).isAfter(doj)) {
            errors.add(new ImportError(n, C_DOB, "Employee must be at least 16 on the date of joining"));
        }
        if (doj != null && doj.isAfter(LocalDate.now().plusYears(1))) {
            errors.add(new ImportError(n, C_DOJ, "Cannot be more than a year in the future"));
        }
        return new ValidRow(n, req, row.text(C_MANAGER), null);
    }

    /**
     * Resolves each row's manager to an existing employee (by code or email) or to another row in
     * the file (by email), and rejects reporting loops among in-file rows.
     */
    private void resolveManagers(List<ValidRow> valid, Map<String, Integer> emailsInFile, List<ImportError> errors) {
        Map<String, String> inFileManagerOf = new HashMap<>();
        for (int i = 0; i < valid.size(); i++) {
            ValidRow v = valid.get(i);
            String ref = v.managerRef();
            if (ref == null || ref.isBlank()) continue;
            String key = ref.trim().toLowerCase();
            String email = v.request().workEmail();
            if (key.equals(email)) {
                errors.add(new ImportError(v.rowNumber(), C_MANAGER, "An employee cannot report to themselves"));
                continue;
            }
            Optional<Employee> existing = key.contains("@")
                    ? employeeRepository.findByWorkEmailIgnoreCase(key)
                    : employeeRepository.findByEmployeeCodeIgnoreCase(key);
            if (existing.isPresent()) {
                if (existing.get().getStatus() == EmployeeStatus.EXITED) {
                    errors.add(new ImportError(v.rowNumber(), C_MANAGER, "Manager " + ref + " has left the organization"));
                } else {
                    valid.set(i, v.withRequest(withManager(v.request(), existing.get().getId())));
                }
            } else if (emailsInFile.containsKey(key)) {
                inFileManagerOf.put(email, key);
                valid.set(i, new ValidRow(v.rowNumber(), v.request(), v.managerRef(), key));
            } else {
                errors.add(new ImportError(v.rowNumber(), C_MANAGER,
                        "No employee with code or email '" + ref + "' (existing or in this file)"));
            }
        }
        // Loop detection among in-file references (A -> B -> A).
        for (ValidRow v : valid) {
            Set<String> seen = new HashSet<>();
            String cur = v.request().workEmail();
            while (cur != null && inFileManagerOf.containsKey(cur)) {
                if (!seen.add(cur)) {
                    errors.add(new ImportError(v.rowNumber(), C_MANAGER, "Reporting loop between rows in this file"));
                    break;
                }
                cur = inFileManagerOf.get(cur);
            }
        }
    }

    // =================================================================== helpers

    private Lookups loadLookups() {
        return new Lookups(byCode(departments.findAllByActiveTrueOrderByNameAsc()),
                byCode(designations.findAllByActiveTrueOrderByNameAsc()),
                byCode(grades.findAllByActiveTrueOrderByNameAsc()),
                byCode(locations.findAllByActiveTrueOrderByNameAsc()));
    }

    private static Map<String, Long> byCode(List<? extends MasterEntity> list) {
        return list.stream().collect(Collectors.toMap(m -> m.getCode().toUpperCase(), MasterEntity::getId, (a, b) -> a));
    }

    private static Long code(ParsedRow row, String col, Map<String, Long> map, int n, List<ImportError> errors) {
        String v = row.text(col);
        if (v == null) return null;
        Long id = map.get(v.trim().toUpperCase());
        if (id == null) {
            errors.add(new ImportError(n, col, "Unknown or inactive code '" + v + "' (see the 'Valid Codes' sheet)"));
        }
        return id;
    }

    /** Accepts "Full Time", "full-time", "FULL_TIME"; also M/F for gender. */
    private static <E extends Enum<E>> E enumValue(ParsedRow row, String col, Class<E> type, int n,
                                                  List<ImportError> errors) {
        String v = row.text(col);
        if (v == null) return null;
        String norm = v.trim().toUpperCase().replaceAll("[\\s-]+", "_");
        if (type == Gender.class) {
            norm = switch (norm) {
                case "M" -> "MALE";
                case "F" -> "FEMALE";
                default -> norm;
            };
        }
        try {
            return Enum.valueOf(type, norm);
        } catch (IllegalArgumentException e) {
            errors.add(new ImportError(n, col, "Invalid value '" + v + "'. Use one of: "
                    + String.join(", ", names(type.getEnumConstants()))));
            return null;
        }
    }

    private static EmployeeRequest withManager(EmployeeRequest r, Long managerId) {
        return new EmployeeRequest(r.firstName(), r.middleName(), r.lastName(), r.workEmail(), r.personalEmail(),
                r.phone(), r.gender(), r.dateOfBirth(), r.maritalStatus(), r.bloodGroup(), r.nationality(),
                r.currentAddress(), r.permanentAddress(), r.departmentId(), r.designationId(), r.gradeId(),
                r.locationId(), managerId, r.employmentType(), r.status(), r.dateOfJoining(),
                r.probationEndDate(), r.noticePeriodDays(), r.annualCtc(), false);
    }

    private static int countValid(List<ParsedRow> rows, List<ImportError> errors) {
        Set<Integer> bad = errors.stream().map(ImportError::row).collect(Collectors.toSet());
        return (int) rows.stream().filter(r -> !bad.contains(r.rowNumber())).count();
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }

    private static String firstCode(MasterRepository<? extends MasterEntity> repo) {
        return repo.findAllByActiveTrueOrderByNameAsc().stream().findFirst().map(MasterEntity::getCode).orElse("");
    }

    private static void writeCodes(Sheet ref, int col, List<? extends MasterEntity> items) {
        for (int i = 0; i < items.size(); i++) {
            Row row = ref.getRow(i + 1) != null ? ref.getRow(i + 1) : ref.createRow(i + 1);
            row.createCell(col).setCellValue(items.get(i).getCode());
            row.createCell(col + 1).setCellValue(items.get(i).getName());
        }
    }

    private static void addDropdown(Sheet sheet, int col, String[] options) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation dv = helper.createValidation(helper.createExplicitListConstraint(options),
                new CellRangeAddressList(1, MAX_IMPORT_ROWS, col, col));
        dv.setShowErrorBox(true);
        sheet.addValidationData(dv);
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static int text(Row row, int col, String value) {
        if (value != null) row.createCell(col).setCellValue(value);
        return col + 1;
    }

    private static int date(Row row, int col, LocalDate value, CellStyle style) {
        if (value != null) {
            Cell c = row.createCell(col);
            c.setCellValue(value);
            c.setCellStyle(style);
        }
        return col + 1;
    }

    private static String name(MasterEntity m) {
        return m == null ? null : m.getName();
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    // =================================================================== row types

    private record Lookups(Map<String, Long> departments, Map<String, Long> designations,
                           Map<String, Long> grades, Map<String, Long> locations) {
    }

    private record ValidRow(int rowNumber, EmployeeRequest request, String managerRef, String inFileManagerEmail) {
        ValidRow withRequest(EmployeeRequest r) {
            return new ValidRow(rowNumber, r, managerRef, inFileManagerEmail);
        }
    }

    /** One non-blank spreadsheet row with typed accessors that report parse errors. */
    private record ParsedRow(int rowNumber, Map<String, Cell> cells) {
        private static final DataFormatter FMT = new DataFormatter();

        String text(String col) {
            Cell c = cells.get(col);
            if (c == null) return null;
            String s = FMT.formatCellValue(c).trim();
            return s.isEmpty() ? null : s;
        }

        LocalDate date(String col, int n, List<ImportError> errors) {
            Cell c = cells.get(col);
            if (c == null) return null;
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();   // no time-zone round trip
            }
            String s = text(col);
            for (DateTimeFormatter f : DATE_FORMATS) {
                try {
                    return LocalDate.parse(s, f);
                } catch (DateTimeParseException ignored) {
                    // try next format
                }
            }
            errors.add(new ImportError(n, col, "Invalid date '" + s + "'. Use YYYY-MM-DD"));
            return null;
        }

        BigDecimal decimal(String col, int n, List<ImportError> errors) {
            Cell c = cells.get(col);
            if (c == null) return null;
            if (c.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(c.getNumericCellValue());
            }
            String s = text(col).replace(",", "");
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                errors.add(new ImportError(n, col, "Invalid number '" + text(col) + "'"));
                return null;
            }
        }
    }
}
