package com.hrgenius.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rupee formatting with Indian digit grouping (30,00,000). Done by hand because java.text's
 * DecimalFormat only supports uniform grouping and would print 3,000,000.
 */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    /** Whole rupees, Indian grouping, no currency prefix; "—" for null. */
    public static String grouped(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        String digits = amount.setScale(0, RoundingMode.HALF_UP).abs().toPlainString();
        StringBuilder sb = new StringBuilder();
        int n = digits.length();
        if (n <= 3) {
            sb.append(digits);
        } else {
            String head = digits.substring(0, n - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    sb.append(',');
                }
                sb.append(head.charAt(i));
            }
            sb.append(',').append(digits, n - 3, n);
        }
        return (amount.signum() < 0 ? "-" : "") + sb;
    }
}
