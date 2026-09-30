package com.hrgenius.employee.seed;

import com.hrgenius.common.security.CryptoConverter;
import com.hrgenius.common.storage.FileStorageService;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V6 demo seed that SQL cannot express:
 * <ul>
 *   <li>bank/statutory IDs, which must be AES-encrypted with the application key, and</li>
 *   <li>a few vault documents (small generated PDFs) with expiry dates relative to the day
 *       the migration runs, so the expiry-alert demo always has fresh data.</li>
 * </ul>
 * All identifiers are FAKE and only format-plausible. Spring Boot hands {@code JavaMigration}
 * beans to Flyway, which is why this can use the same {@link CryptoConverter} as JPA.
 * It lives outside {@code db/migration} so Flyway does not also instantiate it reflectively.
 */
@Component
public class V6__Seed_statutory_and_documents extends BaseJavaMigration {

    private static final String[][] BANKS = {
            {"Metro Commerce Bank", "MCBK"}, {"Unity National Bank", "UNBK"}, {"Coastal Federal Bank", "CFBK"}};

    private final CryptoConverter crypto;
    private final FileStorageService storage;

    public V6__Seed_statutory_and_documents(CryptoConverter crypto, FileStorageService storage) {
        this.crypto = crypto;
        this.storage = storage;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        Timestamp now = Timestamp.from(Instant.now());
        seedStatutory(conn, now);
        seedDocuments(conn, now);
    }

    private void seedStatutory(Connection conn, Timestamp now) throws Exception {
        String insert = "INSERT INTO employee_statutory (employee_id, pan_enc, aadhaar_enc, uan, esi_number, bank_name, "
                + "account_holder_name, bank_account_enc, bank_ifsc, tax_regime, created_at, created_by, deleted) "
                + "VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 'system', 0)";
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT id, first_name, last_name FROM employees ORDER BY id");
             ResultSet rs = select.executeQuery();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            while (rs.next()) {
                long id = rs.getLong(1);
                String first = rs.getString(2);
                String last = rs.getString(3);
                String[] bank = BANKS[(int) (id % BANKS.length)];
                ps.setLong(1, id);
                ps.setString(2, crypto.convertToDatabaseColumn(fakePan(id, last)));
                ps.setString(3, crypto.convertToDatabaseColumn(fakeAadhaar(id)));
                ps.setString(4, "10" + pad(1000000000L + id * 7331, 10));
                ps.setString(5, bank[0]);
                ps.setString(6, first + " " + last);
                ps.setString(7, crypto.convertToDatabaseColumn(pad(5000000000L + id * 104729, 12)));
                ps.setString(8, bank[1] + "0" + pad(100000 + id * 37, 6));
                ps.setString(9, id % 3 == 0 ? "OLD" : "NEW");
                ps.setTimestamp(10, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void seedDocuments(Connection conn, Timestamp now) throws Exception {
        LocalDate today = LocalDate.now();
        // work email -> {category, title, days until expiry (null = never)}
        Map<String, Object[]> docs = new LinkedHashMap<>();
        docs.put("manager@hrgenius.com", new Object[]{"VISA", "Employment visa", 20});
        docs.put("payroll@hrgenius.com", new Object[]{"VISA", "Work permit", 12});
        docs.put("recruiter@hrgenius.com", new Object[]{"VISA", "Employment pass", -5});
        docs.put("karthik.rao@hrgenius.com", new Object[]{"CERTIFICATION", "AWS Solutions Architect - Associate", 25});
        docs.put("anjali.verma@hrgenius.com", new Object[]{"CONTRACT", "Fixed-term contract", 60});
        docs.put("employee@hrgenius.com", new Object[]{"ID_PROOF", "Passport", 2400});
        docs.put("arjun.mehta@hrgenius.com", new Object[]{"EDUCATION", "MBA degree certificate", null});

        String insert = "INSERT INTO employee_documents (id, employee_id, category, title, original_file_name, content_type, "
                + "size_bytes, storage_key, expiry_date, verified, notes, created_at, created_by, deleted) "
                + "VALUES (employee_document_seq.NEXTVAL, ?, ?, ?, ?, 'application/pdf', ?, ?, ?, 1, "
                + "'Seeded demo document', ?, 'system', 0)";
        try (PreparedStatement lookup = conn.prepareStatement("SELECT id FROM employees WHERE work_email = ?");
             PreparedStatement ps = conn.prepareStatement(insert)) {
            for (Map.Entry<String, Object[]> entry : docs.entrySet()) {
                Long employeeId = findEmployeeId(lookup, entry.getKey());
                if (employeeId == null) {
                    continue;   // seed data changed; skip rather than fail the migration
                }
                String category = (String) entry.getValue()[0];
                String title = (String) entry.getValue()[1];
                Integer days = (Integer) entry.getValue()[2];
                byte[] pdf = samplePdf(title, entry.getKey());
                String key = storage.store(pdf, "application/pdf");

                ps.setLong(1, employeeId);
                ps.setString(2, category);
                ps.setString(3, title);
                ps.setString(4, title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".pdf");
                ps.setLong(5, pdf.length);
                ps.setString(6, key);
                if (days == null) {
                    ps.setNull(7, Types.DATE);
                } else {
                    ps.setDate(7, Date.valueOf(today.plusDays(days)));
                }
                ps.setTimestamp(8, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Long findEmployeeId(PreparedStatement lookup, String workEmail) throws Exception {
        lookup.setString(1, workEmail);
        try (ResultSet rs = lookup.executeQuery()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private static byte[] samplePdf(String title, String owner) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("HRGenius demo document"));
        doc.add(new Paragraph(title));
        doc.add(new Paragraph("Employee: " + owner));
        doc.add(new Paragraph("This is placeholder content generated for demonstration only."));
        doc.close();
        return out.toByteArray();
    }

    /** Format AAAPL9999A: 5 letters (4th = 'P' for individual, 5th = surname initial), 4 digits, 1 letter. */
    private static String fakePan(long id, String lastName) {
        char a = (char) ('A' + id % 26);
        char b = (char) ('A' + (id * 7) % 26);
        char c = (char) ('A' + (id * 13) % 26);
        char initial = Character.toUpperCase(lastName.charAt(0));
        char check = (char) ('A' + (id * 3) % 26);
        return "" + a + b + c + 'P' + initial + pad(1000 + id * 17 % 9000, 4) + check;
    }

    /** 12 digits, first digit 2-9 as for real Aadhaar numbers; not checksum-valid (fake). */
    private static String fakeAadhaar(long id) {
        return (2 + id % 8) + pad(10000000000L + id * 982451653L % 89999999999L, 11);
    }

    private static String pad(long n, int width) {
        String s = Long.toString(Math.abs(n));
        if (s.length() > width) {
            return s.substring(s.length() - width);
        }
        return "0".repeat(width - s.length()) + s;
    }
}
