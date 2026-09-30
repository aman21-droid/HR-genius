package com.hrgenius.payroll.calc;

import com.hrgenius.payroll.calc.TaxCalculator.Regime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes one employee's payslip for one month. Pure and framework-free so it is unit-testable
 * and reusable from the demo-seed migration.
 *
 * <p>Model (typical Indian CTC structure):
 * <ul>
 *   <li>Monthly CTC = annual CTC / 12, and includes employer PF (12% of Basic, on a wage capped at
 *       ₹15,000) and, when applicable, employer ESI (3.25% of gross).</li>
 *   <li>Earnings come from the configured components; the BALANCING component absorbs whatever
 *       remains of monthly gross.</li>
 *   <li>Everything is pro-rated by paid days / days in the month (loss-of-pay and partial months).
 *       One-off adjustments are not pro-rated.</li>
 *   <li>Deductions: employee PF 12% (same capped wage), ESI 0.75% when full-month gross is at most
 *       ₹21,000, professional tax ₹200 when gross is at least ₹15,000, and TDS = estimated annual
 *       tax / 12.</li>
 * </ul>
 * Amounts are rounded to whole rupees.
 */
public final class PayrollCalculator {

    public enum CalcType { PERCENT_OF_CTC, PERCENT_OF_BASIC, FIXED_MONTHLY, BALANCING }

    public enum LineType { EARNING, DEDUCTION, EMPLOYER }

    public record Component(String code, String name, CalcType calcType, BigDecimal value, boolean taxable) {
    }

    public record Adjustment(boolean earning, String label, BigDecimal amount, boolean taxable) {
    }

    public record Input(BigDecimal annualCtc, List<Component> components, int daysInPeriod, BigDecimal paidDays,
                        Regime regime, List<Adjustment> adjustments) {
    }

    public record Line(String code, String name, LineType type, BigDecimal amount) {
    }

    public record Result(List<Line> lines, BigDecimal gross, BigDecimal deductions, BigDecimal net,
                         BigDecimal employerPf, BigDecimal employerEsi) {

        public BigDecimal employerCost() {
            return gross.add(employerPf).add(employerEsi);
        }
    }

    static final BigDecimal PF_RATE = new BigDecimal("0.12");
    static final BigDecimal PF_WAGE_CAP = new BigDecimal("15000");
    static final BigDecimal ESI_THRESHOLD = new BigDecimal("21000");
    static final BigDecimal ESI_EMPLOYEE = new BigDecimal("0.0075");
    static final BigDecimal ESI_EMPLOYER = new BigDecimal("0.0325");
    static final BigDecimal PT_THRESHOLD = new BigDecimal("15000");
    static final BigDecimal PT_AMOUNT = new BigDecimal("200");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private PayrollCalculator() {
    }

    public static Result calculate(Input in) {
        BigDecimal monthlyCtc = in.annualCtc().divide(TWELVE, 4, RoundingMode.HALF_UP);
        BigDecimal factor = in.daysInPeriod() == 0 ? BigDecimal.ZERO
                : in.paidDays().divide(BigDecimal.valueOf(in.daysInPeriod()), 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);

        // ---- full-month structure
        BigDecimal basicFull = BigDecimal.ZERO;
        for (Component c : in.components()) {
            if (c.calcType() == CalcType.PERCENT_OF_CTC && "BASIC".equals(c.code())) {
                basicFull = pct(monthlyCtc, c.value());
            }
        }
        BigDecimal erPfFull = PF_RATE.multiply(basicFull.min(PF_WAGE_CAP));
        BigDecimal grossFull = monthlyCtc.subtract(erPfFull);
        boolean esi = grossFull.divide(BigDecimal.ONE.add(ESI_EMPLOYER), 4, RoundingMode.HALF_UP)
                .compareTo(ESI_THRESHOLD) <= 0;
        if (esi) {
            grossFull = grossFull.divide(BigDecimal.ONE.add(ESI_EMPLOYER), 4, RoundingMode.HALF_UP);
        }

        List<BigDecimal> fullAmounts = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        int balancingIndex = -1;
        for (int i = 0; i < in.components().size(); i++) {
            Component c = in.components().get(i);
            BigDecimal amount = switch (c.calcType()) {
                case PERCENT_OF_CTC -> pct(monthlyCtc, c.value());
                case PERCENT_OF_BASIC -> pct(basicFull, c.value());
                case FIXED_MONTHLY -> c.value();
                case BALANCING -> BigDecimal.ZERO;
            };
            if (c.calcType() == CalcType.BALANCING) {
                balancingIndex = i;
            } else {
                // Never allocate more than the gross available (very low CTCs).
                amount = amount.min(grossFull.subtract(allocated).max(BigDecimal.ZERO));
                allocated = allocated.add(amount);
            }
            fullAmounts.add(amount);
        }
        if (balancingIndex >= 0) {
            fullAmounts.set(balancingIndex, grossFull.subtract(allocated).max(BigDecimal.ZERO));
        }

        // ---- this month's earnings (pro-rated)
        List<Line> lines = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal taxableGross = BigDecimal.ZERO;
        BigDecimal basic = BigDecimal.ZERO;
        for (int i = 0; i < in.components().size(); i++) {
            Component c = in.components().get(i);
            BigDecimal amount = rupees(fullAmounts.get(i).multiply(factor));
            if ("BASIC".equals(c.code())) {
                basic = amount;
            }
            if (amount.signum() > 0) {
                lines.add(new Line(c.code(), c.name(), LineType.EARNING, amount));
            }
            gross = gross.add(amount);
            if (c.taxable()) {
                taxableGross = taxableGross.add(amount);
            }
        }
        BigDecimal taxableAdjustments = BigDecimal.ZERO;
        int n = 1;
        for (Adjustment a : in.adjustments()) {
            if (a.earning()) {
                BigDecimal amount = rupees(a.amount());
                lines.add(new Line("ADJ" + n++, a.label(), LineType.EARNING, amount));
                gross = gross.add(amount);
                if (a.taxable()) {
                    taxableAdjustments = taxableAdjustments.add(amount);
                }
            }
        }

        // ---- statutory deductions
        BigDecimal deductions = BigDecimal.ZERO;
        BigDecimal pfWage = basic.min(PF_WAGE_CAP);
        BigDecimal eePf = rupees(PF_RATE.multiply(pfWage));
        BigDecimal erPf = eePf;
        if (eePf.signum() > 0) {
            lines.add(new Line("PF", "Provident fund (employee)", LineType.DEDUCTION, eePf));
            deductions = deductions.add(eePf);
        }
        BigDecimal erEsi = BigDecimal.ZERO;
        if (esi && gross.signum() > 0) {
            BigDecimal eeEsi = gross.multiply(ESI_EMPLOYEE).setScale(0, RoundingMode.CEILING);
            erEsi = gross.multiply(ESI_EMPLOYER).setScale(0, RoundingMode.CEILING);
            lines.add(new Line("ESI", "ESI (employee)", LineType.DEDUCTION, eeEsi));
            deductions = deductions.add(eeEsi);
        }
        if (gross.compareTo(PT_THRESHOLD) >= 0) {
            lines.add(new Line("PT", "Professional tax", LineType.DEDUCTION, PT_AMOUNT));
            deductions = deductions.add(PT_AMOUNT);
        }
        if (gross.signum() > 0) {
            // Annualise the full-month structure, then add this month's taxable one-offs.
            BigDecimal fullTaxable = BigDecimal.ZERO;
            for (int i = 0; i < in.components().size(); i++) {
                if (in.components().get(i).taxable()) {
                    fullTaxable = fullTaxable.add(fullAmounts.get(i));
                }
            }
            BigDecimal annualGross = fullTaxable.multiply(TWELVE).add(taxableAdjustments);
            BigDecimal annualPf = PF_RATE.multiply(basicFull.min(PF_WAGE_CAP)).multiply(TWELVE);
            BigDecimal tds = rupees(TaxCalculator.annualTax(annualGross, annualPf, in.regime())
                    .divide(TWELVE, 4, RoundingMode.HALF_UP).multiply(factor.signum() > 0 ? BigDecimal.ONE : BigDecimal.ZERO));
            if (tds.signum() > 0) {
                lines.add(new Line("TDS", "Income tax (TDS)", LineType.DEDUCTION, tds));
                deductions = deductions.add(tds);
            }
        }
        n = 1;
        for (Adjustment a : in.adjustments()) {
            if (!a.earning()) {
                BigDecimal amount = rupees(a.amount());
                lines.add(new Line("DED" + n++, a.label(), LineType.DEDUCTION, amount));
                deductions = deductions.add(amount);
            }
        }

        if (erPf.signum() > 0) {
            lines.add(new Line("ER_PF", "Provident fund (employer)", LineType.EMPLOYER, erPf));
        }
        if (erEsi.signum() > 0) {
            lines.add(new Line("ER_ESI", "ESI (employer)", LineType.EMPLOYER, erEsi));
        }
        BigDecimal net = gross.subtract(deductions).max(BigDecimal.ZERO);
        return new Result(lines, gross, deductions, net, erPf, erEsi);
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal rupees(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }
}
