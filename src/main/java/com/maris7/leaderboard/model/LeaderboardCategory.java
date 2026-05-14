package com.maris7.leaderboard.model;

import java.util.Locale;
import java.util.Objects;

public final class LeaderboardCategory {
    private final String key;
    private final String displayName;
    private final boolean compact;
    private final String placeholder;

    public LeaderboardCategory(String key, String displayName, boolean compact, String placeholder) {
        this.key = key;
        this.displayName = displayName;
        this.compact = compact;
        this.placeholder = placeholder;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public boolean compact() { return compact; }
    public String placeholder() { return placeholder; }

    public static String normalizeKey(String input) {
        if (input == null || input.isBlank()) {
            return "leaderboard";
        }
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    public static String defaultDisplayName(String key) {
        String normalized = normalizeKey(key).replace('_', ' ');
        String[] parts = normalized.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.isEmpty() ? "Leaderboard" : out.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LeaderboardCategory that)) {
            return false;
        }
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
