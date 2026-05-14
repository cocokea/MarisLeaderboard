package com.maris7.leaderboard.util;

import java.text.DecimalFormat;

public final class NumberFormatUtil {
    private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

    private NumberFormatUtil() {}

    public static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000d) return DECIMAL.format(value / 1_000_000_000_000d) + "T";
        if (abs >= 1_000_000_000d) return DECIMAL.format(value / 1_000_000_000d) + "B";
        if (abs >= 1_000_000d) return DECIMAL.format(value / 1_000_000d) + "M";
        if (abs >= 1_000d) return DECIMAL.format(value / 1_000d) + "K";
        if (Math.floor(value) == value) return String.valueOf((long) value);
        return DECIMAL.format(value);
    }

    public static double parseNumber(String input) {
        if (input == null || input.isBlank()) return 0D;
        String clean = input.replaceAll("[^0-9.,-]", "").replace(",", "");
        if (clean.isBlank() || clean.equals("-") || clean.equals(".")) return 0D;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }
}
