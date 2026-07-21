package com.wildmare.wmorder.order.model;

public enum OrderSort {
    HIGHEST_PRICE,
    HIGHEST_TOTAL_VALUE,
    NEWEST,
    OLDEST,
    EXPIRING_SOON,
    LARGEST_REMAINING;

    public static OrderSort parse(String value) {
        if (value == null) return NEWEST;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return NEWEST;
        }
    }
}
