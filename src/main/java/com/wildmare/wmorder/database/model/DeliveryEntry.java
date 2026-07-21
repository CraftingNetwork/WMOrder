package com.wildmare.wmorder.database.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public record DeliveryEntry(UUID id, UUID orderId, UUID ownerUuid, DeliveryType type, byte[] itemBlob,
                            long quantity, BigDecimal amount, DeliveryStatus status, UUID claimToken,
                            UUID transactionId, String note, Instant createdAt, Instant updatedAt) {
    public DeliveryEntry {
        itemBlob = itemBlob == null ? null : Arrays.copyOf(itemBlob, itemBlob.length);
    }
    @Override public byte[] itemBlob() { return itemBlob == null ? null : Arrays.copyOf(itemBlob, itemBlob.length); }
    public boolean isItem() { return itemBlob != null && quantity > 0; }
    public boolean isMoney() { return amount != null && amount.signum() > 0; }
}
