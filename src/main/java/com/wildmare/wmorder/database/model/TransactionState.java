package com.wildmare.wmorder.database.model;

public enum TransactionState {
    PREPARED,
    ECONOMY_APPLIED,
    ITEMS_SECURED,
    COMMITTED,
    COMPENSATED,
    RECOVERY_PENDING,
    ADMIN_REVIEW,
    FAILED;

    public boolean unresolved() {
        return this == PREPARED || this == ECONOMY_APPLIED || this == ITEMS_SECURED
                || this == RECOVERY_PENDING || this == ADMIN_REVIEW;
    }
}
