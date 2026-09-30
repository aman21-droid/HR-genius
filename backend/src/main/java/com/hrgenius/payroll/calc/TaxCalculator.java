package com.hrgenius.payroll.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indian income tax estimate for salaried employees (FY 2025-26 slabs). Deliberately simple:
 * standard deduction only (plus employee PF under 80C for the old regime); no other
 * declarations, surcharge or marginal relief. Good enough for monthly TDS estimation.
 */
public final class TaxCalculator {

    public enum Regime { NEW, OLD }

    private static final BigDecimal CESS = new BigDecimal("0.04");

    /** {upper bound of slab (exclusive of next), rate}. Last bound is effectively infinity. */
    private static final long[][] NEW_SLABS = {
            {400_000, 0}, {800_000, 5}, {1_200_000, 10}, {1_600_000, 15},
            {2_000_000, 20}, {2_400_000, 25}, {Long.MAX_VALUE, 30}};
    private static final long[][] OLD_SLABS = {
            {250_000, 0}, {500_000, 5}, {1_000_000, 20}, {Long.MAX_VALUE, 30}};

    static final BigDecimal NEW_STANDARD_DEDUCTION = new BigDecimal("75000");
    static final BigDecimal OLD_STANDARD_DEDUCTION = new BigDecimal("50000");
    static final BigDecimal NEW_REBATE_LIMIT = new BigDecimal("1200000");   // section 87A
    static final BigDecimal OLD_REBATE_LIMIT = new BigDecimal("500000");
    static final BigDecimal SECTION_80C_CAP = new BigDecimal("150000");

    private TaxCalculator() {
    }

    /**
     * Annual tax (including 4% cess) on an annual gross salary.
     *
     * @param annualGross       taxable salary for the year before deductions
     * @param annualEmployeePf  employee PF for the year (80C, old regime only)
     */
    public static BigDecimal annualTax(BigDecimal annualGross, BigDecimal annualEmployeePf, Regime regime) {
        BigDecimal taxable = taxableIncome(annualGross, annualEmployeePf, regime);
        BigDecimal rebateLimit = regime == Regime.NEW ? NEW_REBATE_LIMIT : OLD_REBATE_LIMIT;
        if (taxable.compareTo(rebateLimit) <= 0) {
            return BigDecimal.ZERO;        // full rebate under section 87A
        }
        BigDecimal tax = slabTax(taxable, regime == Regime.NEW ? NEW_SLABS : OLD_SLABS);
        return tax.add(tax.multiply(CESS)).setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal taxableIncome(BigDecimal annualGross, BigDecimal annualEmployeePf, Regime regime) {
        BigDecimal taxable = annualGross.subtract(
                regime == Regime.NEW ? NEW_STANDARD_DEDUCTION : OLD_STANDARD_DEDUCTION);
        if (regime == Regime.OLD && annualEmployeePf != null) {
            taxable = taxable.subtract(annualEmployeePf.min(SECTION_80C_CAP));
        }
        return taxable.max(BigDecimal.ZERO);
    }

    private static BigDecimal slabTax(BigDecimal income, long[][] slabs) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (long[] slab : slabs) {
            BigDecimal upper = BigDecimal.valueOf(slab[0]);
            if (income.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal band = income.min(upper).subtract(lower);
            tax = tax.add(band.multiply(BigDecimal.valueOf(slab[1])).divide(BigDecimal.valueOf(100)));
            lower = upper;
        }
        return tax;
    }
}
