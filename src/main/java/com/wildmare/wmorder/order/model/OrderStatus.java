package com.wildmare.wmorder.order.model;

public enum OrderStatus {
    ACTIVE,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    EXPIRED,
    PENDING_COLLECTION,
    REFUND_PENDING,
    COMPLETED,
    ADMIN_FROZEN;

    public boolean acceptsFulfillment() {
        return this == ACTIVE || this == PARTIALLY_FILLED;
    }

    public boolean cancellable() {
        return this == ACTIVE || this == PARTIALLY_FILLED;
    }

    public boolean terminal() {
        return this == CANCELLED || this == EXPIRED || this == COMPLETED;
    }
}
