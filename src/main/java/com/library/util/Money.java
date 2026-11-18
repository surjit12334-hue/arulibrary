package com.library.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats money as Indian Rupees with Indian digit grouping.
 * Examples: ₹1.00, ₹1,000.00, ₹1,00,000.50, ₹1,23,45,678.90.
 */
public final class Money {

    public static final String SYMBOL = "\u20B9";

    private Money() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        String plain = value.toPlainString();
        boolean negative = plain.startsWith("-");
        if (negative) {
            plain = plain.substring(1);
        }

        int dot = plain.indexOf('.');
        String digits = dot < 0 ? plain : plain.substring(0, dot);
        String decimals = dot < 0 ? ".00" : plain.substring(dot);

        // Indian grouping: last three digits, then pairs of two from the right.
        StringBuilder grouped = new StringBuilder();
        int end = digits.length();
        int start = Math.max(0, end - 3);
        grouped.append(digits, start, end);
        while (start > 0) {
            end = start;
            start = Math.max(0, end - 2);
            grouped.insert(0, ',');
            grouped.insert(0, digits, start, end);
        }

        return (negative ? "-" : "") + SYMBOL + grouped + decimals;
    }
}
