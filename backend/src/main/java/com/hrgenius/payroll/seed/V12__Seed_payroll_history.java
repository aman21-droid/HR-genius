package com.hrgenius.payroll.seed;

import com.hrgenius.common.security.CryptoConverter;
import com.hrgenius.common.util.MaskingUtil;
import com.hrgenius.payroll.calc.PayrollCalculator;
import com.hrgenius.payroll.calc.PayrollCalculator.*;
import com.hrgenius.payroll.calc.TaxCalculator.Regime;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo payroll history computed with the real {@link PayrollCalculator}: August 2026 PAID and
 * September 2026 APPROVED (ready to be marked paid), so payslips, exports and analytics have data
 * from the first boot. Like V6 this is a Spring bean so it can decrypt bank numbers for masking.
 */
@Component
public class V12__Seed_payroll_history extends BaseJavaMigration {

    private final CryptoConverter crypto;

    public V12__Seed_payroll_history(CryptoConverter crypto) {
        this.crypto = crypto;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<PayrollCalculator.Component> structure = loadStructure(conn);
        seedRun(conn, structure, YearMonth.of(2026, 8), "PAID", "NEFT-AUG26-0001");
        seedRun(conn, structure, YearMonth.of(2026, 9), "APPROVED", null);
    }

    private List<PayrollCalculator.Component> loadStructure(Connection conn) throws SQLException {
        List<PayrollCalculator.Component> list = new ArrayList<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT code, name, calc_type, calc_value, taxable FROM salary_components "
                     + "WHERE active = 1 AND deleted = 0 ORDER BY sort_order, id")) {
            while (rs.next()) {
                list.add(new PayrollCalculator.Component(rs.getString(1), rs.getString(2),
                        CalcType.valueOf(rs.getString(3)), rs.getBigDecimal(4), rs.getInt(5) == 1));
            }
        }
        return list;
    }

    private void seedRun(Connection conn, List<PayrollCalculator.Component> structure, YearMonth ym, String status,
                         String paymentRef) throws SQLException {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        int days = ym.lengthOfMonth();
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp approvedAt = Timestamp.valueOf(end.plusDays(1).atTime(11, 0));
        Timestamp paidAt = "PAID".equals(status) ? Timestamp.valueOf(end.plusDays(1).atTime(16, 0)) : null;

        long runId = nextVal(conn, "payroll_run_seq");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll_runs (id, period, period_start, period_end, "
                + "status, employee_count, total_gross, total_deductions, total_net, total_employer_cost, calculated_at, "
                + "approved_at, paid_at, payment_reference, created_at, created_by, deleted) "
                + "VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, 0, ?, ?, ?, ?, ?, 'payroll@hrgenius.com', 0)")) {
            ps.setLong(1, runId);
            ps.setString(2, ym.toString());
            ps.setDate(3, Date.valueOf(start));
            ps.setDate(4, Date.valueOf(end));
            ps.setString(5, status);
            ps.setTimestamp(6, approvedAt);
            ps.setTimestamp(7, approvedAt);
            ps.setTimestamp(8, paidAt);
            ps.setString(9, paymentRef);
            ps.setTimestamp(10, now);
            ps.executeUpdate();
        }

        String select = "SELECT e.id, e.annual_ctc, e.date_of_joining, e.exit_date, s.tax_regime, s.bank_name, "
                + "s.bank_account_enc, s.bank_ifsc FROM employees e LEFT JOIN employee_statutory s ON s.employee_id = e.id "
                + "WHERE e.deleted = 0 AND e.date_of_joining <= ? AND (e.exit_date IS NULL OR e.exit_date >= ?) "
                + "AND e.annual_ctc > 0 ORDER BY e.employee_code";
        int count = 0;
        BigDecimal gross = BigDecimal.ZERO, ded = BigDecimal.ZERO, net = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setDate(1, Date.valueOf(end));
            ps.setDate(2, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long empId = rs.getLong(1);
                    BigDecimal ctc = rs.getBigDecimal(2);
                    LocalDate doj = rs.getDate(3).toLocalDate();
                    LocalDate exit = rs.getDate(4) != null ? rs.getDate(4).toLocalDate() : null;
                    Regime regime = "OLD".equals(rs.getString(5)) ? Regime.OLD : Regime.NEW;
                    LocalDate from = doj.isAfter(start) ? doj : start;
                    LocalDate to = exit != null && exit.isBefore(end) ? exit : end;
                    BigDecimal paidDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1);

                    Result res = PayrollCalculator.calculate(new Input(ctc, structure, days, paidDays, regime, List.of()));
                    String account = rs.getString(7) != null ? crypto.convertToEntityAttribute(rs.getString(7)) : null;
                    insertPayslip(conn, runId, empId, days, paidDays, ctc, res, regime, rs.getString(6),
                            account != null ? MaskingUtil.mask(account, 4) : null, rs.getString(8), now);
                    count++;
                    gross = gross.add(res.gross());
                    ded = ded.add(res.deductions());
                    net = net.add(res.net());
                    cost = cost.add(res.employerCost());
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE payroll_runs SET employee_count = ?, total_gross = ?, "
                + "total_deductions = ?, total_net = ?, total_employer_cost = ? WHERE id = ?")) {
            ps.setInt(1, count);
            ps.setBigDecimal(2, gross);
            ps.setBigDecimal(3, ded);
            ps.setBigDecimal(4, net);
            ps.setBigDecimal(5, cost);
            ps.setLong(6, runId);
            ps.executeUpdate();
        }
    }

    private void insertPayslip(Connection conn, long runId, long empId, int days, BigDecimal paidDays, BigDecimal ctc,
                               Result res, Regime regime, String bank, String accountMasked, String ifsc, Timestamp now)
            throws SQLException {
        long slipId = nextVal(conn, "payslip_seq");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payslips (id, run_id, employee_id, days_in_period, "
                + "lop_days, paid_days, annual_ctc, gross_earnings, total_deductions, net_pay, employer_pf, employer_esi, "
                + "tax_regime, bank_name, account_masked, bank_ifsc, created_at, created_by, deleted) "
                + "VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'payroll@hrgenius.com', 0)")) {
            ps.setLong(1, slipId);
            ps.setLong(2, runId);
            ps.setLong(3, empId);
            ps.setInt(4, days);
            ps.setBigDecimal(5, paidDays);
            ps.setBigDecimal(6, ctc);
            ps.setBigDecimal(7, res.gross());
            ps.setBigDecimal(8, res.deductions());
            ps.setBigDecimal(9, res.net());
            ps.setBigDecimal(10, res.employerPf());
            ps.setBigDecimal(11, res.employerEsi());
            ps.setString(12, regime.name());
            ps.setString(13, bank);
            ps.setString(14, accountMasked);
            ps.setString(15, ifsc);
            ps.setTimestamp(16, now);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payslip_lines (id, payslip_id, code, name, "
                + "line_type, amount, sort_order, created_at, created_by, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'system', 0)")) {
            int order = 0;
            for (Line l : res.lines()) {
                ps.setLong(1, nextVal(conn, "payslip_line_seq"));
                ps.setLong(2, slipId);
                ps.setString(3, l.code());
                ps.setString(4, l.name());
                ps.setString(5, l.type().name());
                ps.setBigDecimal(6, l.amount());
                ps.setInt(7, order++);
                ps.setTimestamp(8, now);
                ps.executeUpdate();
            }
        }
    }

    private static long nextVal(Connection conn, String sequence) throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT " + sequence + ".NEXTVAL FROM dual")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
