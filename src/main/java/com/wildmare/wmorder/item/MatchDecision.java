package com.wildmare.wmorder.item;

/** Pure matching decision logic, separated from Bukkit item access for deterministic tests. */
public final class MatchDecision {
    private MatchDecision() {}

    public static boolean matches(MatchingMode mode, boolean materialEqual, boolean similarEqual, boolean fingerprintEqual) {
        if (!materialEqual) return false;
        return switch (mode) {
            case MATERIAL_ONLY -> true;
            case SIMILAR -> similarEqual;
            case EXACT -> fingerprintEqual;
        };
    }
}
