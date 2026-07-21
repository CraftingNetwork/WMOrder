package com.wildmare.wmorder.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record BuyOrder(
        UUID id,
        UUID buyerUuid,
        String buyerName,
        String itemFingerprint,
        String itemMaterial,
        String itemDisplayName,
        byte[] itemBlob,
        long requestedQuantity,
        long remainingQuantity,
        long fulfilledQuantity,
        BigDecimal pricePerItem,
        BigDecimal originalTotal,
        BigDecimal remainingReservedBalance,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt,
        OrderStatus status,
        String category,
        String serverId,
        long version,
        String idempotencyKey
) {
    public BuyOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(buyerUuid, "buyerUuid");
        Objects.requireNonNull(buyerName, "buyerName");
        Objects.requireNonNull(itemFingerprint, "itemFingerprint");
        Objects.requireNonNull(itemMaterial, "itemMaterial");
        Objects.requireNonNull(itemDisplayName, "itemDisplayName");
        Objects.requireNonNull(itemBlob, "itemBlob");
        Objects.requireNonNull(pricePerItem, "pricePerItem");
        Objects.requireNonNull(originalTotal, "originalTotal");
        Objects.requireNonNull(remainingReservedBalance, "remainingReservedBalance");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        itemBlob = Arrays.copyOf(itemBlob, itemBlob.length);
    }

    @Override
    public byte[] itemBlob() {
        return Arrays.copyOf(itemBlob, itemBlob.length);
    }

    public boolean acceptsFulfillment(Instant now) {
        return status.acceptsFulfillment() && expiresAt.isAfter(now) && remainingQuantity > 0;
    }
}
