package com.wildmare.wmorder.permission;

import java.math.BigDecimal;
import java.time.Duration;

public record LimitProfile(String name, String permission, int priority, int maxActiveOrders,
                           long maxQuantityPerOrder, BigDecimal maxTotalValue, Duration duration,
                           int creationCooldownSeconds, int fulfillmentCooldownSeconds,
                           BigDecimal taxReductionPercent, boolean listingFeeExempt) {
}
