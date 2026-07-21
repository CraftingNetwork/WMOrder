package com.wildmare.wmorder.item;

public enum MatchingMode {
    MATERIAL_ONLY,
    SIMILAR,
    EXACT;

    public static MatchingMode parse(String value) {
        try {
            return valueOf(value == null ? "EXACT" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return EXACT;
        }
    }
}
