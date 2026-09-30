package com.hrgenius.payroll.calc;

import com.hrgenius.payroll.calc.PayrollCalculator.*;
import com.hrgenius.payroll.calc.TaxCalculator.Regime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Hand-checked examples for the payroll maths. */
class PayrollCalculatorTest {

    private static final List<Component> STRUCTURE = List.of(
            new Component("BASIC", "Basic", CalcType.PERCENT_OF_CTC, new BigDecimal("40"), true),
            new Component("HRA", "HRA", CalcType.PERCENT_OF_BASIC, new BigDecimal("50"), true),
            new Component("CONV", "Conveyance", CalcType.FIXED_MONTHLY, new BigDecimal("1600"), true),
            new Component("SPECIAL", "Special", CalcType.BALANCING, BigDecimal.ZERO, true));

    private static Result run(String ctc, int days, String paidDays, Regime regime, List<Adjustment> adj) {
        return PayrollCalculator.calculate(new Input(new BigDecimal(ctc), STRUCTURE, days, new BigDecimal(paidDays), regime, adj));
    }

    private static Map<String, BigDecimal> byCode(Result r) {
        return r.lines().stream().collect(Collectors.toMap(Line::code, Line::amount));
    }

    @Test
    void twelveLakhCtcFullMonthNewRegimeHasNoTaxThanksToRebate() {
        Result r = run("1200000", 30, "30", Regime.NEW, List.of());
        Map<String, BigDecimal> l = byCode(r);
        // monthly CTC 1,00,000; employer PF 1,800 inside CTC -> gross 98,200
        assertThat(l.get("BASIC")).isEqualByComparingTo("40000");
        assertThat(l.get("HRA")).isEqualByComparingTo("20000");
        assertThat(l.get("CONV")).isEqualByComparingTo("1600");
        assertThat(l.get("SPECIAL")).isEqualByComparingTo("36600");
        assertThat(r.gross()).isEqualByComparingTo("98200");
        assertThat(l.get("PF")).isEqualByComparingTo("1800");       // 12% of the 15,000 wage cap
        assertThat(l.get("PT")).isEqualByComparingTo("200");
        assertThat(l).doesNotContainKey("TDS");                     // taxable 11,03,400 <= 12L rebate limit
        assertThat(l).doesNotContainKey("ESI");
        assertThat(r.net()).isEqualByComparingTo("96200");
        assertThat(r.employerPf()).isEqualByComparingTo("1800");
        assertThat(r.employerCost()).isEqualByComparingTo("100000"); // gross + employer PF == monthly CTC
    }

    @Test
    void twentyFourLakhCtcPaysSlabTaxWithCess() {
        Result r = run("2400000", 30, "30", Regime.NEW, List.of());
        // gross 1,98,200/month -> annual 23,78,400, taxable 23,03,400
        // slabs: 20k + 40k + 60k + 80k + 25% of 3,03,400 (75,850) = 2,75,850; +4% cess = 2,86,884 -> /12 = 23,907
        assertThat(r.gross()).isEqualByComparingTo("198200");
        assertThat(byCode(r).get("TDS")).isEqualByComparingTo("23907");
        assertThat(r.net()).isEqualByComparingTo("172293");
    }

    @Test
    void lowCtcIsCoveredByEsi() {
        Result r = run("240000", 30, "30", Regime.NEW, List.of());
        Map<String, BigDecimal> l = byCode(r);
        // monthly 20,000 - employer PF 960 = 19,040, which also funds employer ESI (3.25%) -> gross 18,441
        assertThat(r.gross()).isEqualByComparingTo("18441");
        assertThat(l.get("PF")).isEqualByComparingTo("960");
        assertThat(l.get("ESI")).isEqualByComparingTo("139");      // ceil(0.75%)
        assertThat(r.employerEsi()).isEqualByComparingTo("600");   // ceil(3.25%)
        assertThat(l.get("PT")).isEqualByComparingTo("200");
        assertThat(r.net()).isEqualByComparingTo("17142");
    }

    @Test
    void halfMonthIsProRatedButPfStaysOnCappedWage() {
        Result r = run("1200000", 30, "15", Regime.NEW, List.of());
        Map<String, BigDecimal> l = byCode(r);
        assertThat(l.get("BASIC")).isEqualByComparingTo("20000");
        assertThat(r.gross()).isEqualByComparingTo("49100");
        assertThat(l.get("PF")).isEqualByComparingTo("1800");      // basic 20,000 still above the 15,000 cap
    }

    @Test
    void oneOffAdjustmentsAreAddedWithoutProRating() {
        Result r = run("1200000", 30, "30", Regime.NEW, List.of(
                new Adjustment(true, "Joining bonus", new BigDecimal("25000"), true),
                new Adjustment(false, "Laptop recovery", new BigDecimal("5000"), false)));
        Map<String, BigDecimal> l = byCode(r);
        assertThat(l.get("ADJ1")).isEqualByComparingTo("25000");
        assertThat(l.get("DED1")).isEqualByComparingTo("5000");
        assertThat(r.gross()).isEqualByComparingTo("123200");
    }

    @Test
    void zeroPaidDaysMeansNothingToPay() {
        Result r = run("1200000", 30, "0", Regime.NEW, List.of());
        assertThat(r.gross()).isEqualByComparingTo("0");
        assertThat(r.net()).isEqualByComparingTo("0");
        assertThat(r.lines()).isEmpty();
    }

    @Test
    void oldRegimeUsesOldSlabsAnd80cForPf() {
        // annual gross 11,78,400 - 50,000 std - 21,600 PF = 11,06,800 taxable
        // 0 + 12,500 + 1,00,000 + 30% of 1,06,800 (32,040) = 1,44,540; +4% = 1,50,322
        assertThat(TaxCalculator.annualTax(new BigDecimal("1178400"), new BigDecimal("21600"), Regime.OLD))
                .isEqualByComparingTo("150322");
        assertThat(TaxCalculator.annualTax(new BigDecimal("500000"), BigDecimal.ZERO, Regime.OLD))
                .isEqualByComparingTo("0");                        // 87A rebate
    }
}
