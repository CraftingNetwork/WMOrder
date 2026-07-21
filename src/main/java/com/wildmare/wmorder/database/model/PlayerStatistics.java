package com.wildmare.wmorder.database.model;

import java.math.BigDecimal;

public record PlayerStatistics(long activeOrders, long completedOrders, long pendingCollection,
                               BigDecimal totalSpent, BigDecimal totalEarned, BigDecimal highestOrder,
                               BigDecimal marketVolume) {
    public static PlayerStatistics empty() {
        return new PlayerStatistics(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
