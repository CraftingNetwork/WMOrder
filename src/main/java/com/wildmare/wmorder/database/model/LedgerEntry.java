package com.wildmare.wmorder.database.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(UUID id, String idempotencyKey, UUID orderId, UUID playerUuid,
                          TransactionType type, BigDecimal gross, BigDecimal fee, BigDecimal net,
                          String economyResponse, TransactionState state, String metadata,
                          Instant createdAt, Instant updatedAt) {
}
