package com.wildmare.wmorder.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern SIMPLE = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);
    private DurationParser() {}

    public static Duration parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Duration is blank");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("p")) return Duration.parse(normalized.toUpperCase(Locale.ROOT));
        Matcher matcher = SIMPLE.matcher(normalized);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid duration: " + value);
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7));
            default -> throw new IllegalArgumentException("Invalid duration unit");
        };
    }

    public static String compact(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds >= 86400) return (seconds / 86400) + "d";
        if (seconds >= 3600) return (seconds / 3600) + "h";
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }
}
