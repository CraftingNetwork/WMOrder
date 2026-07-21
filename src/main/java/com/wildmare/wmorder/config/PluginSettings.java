package com.wildmare.wmorder.config;

import com.wildmare.wmorder.permission.LimitProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

public record PluginSettings(String serverId, String locale, OrderSettings orders, PerformanceSettings performance,
                             EconomySettings economy, NotificationSettings notifications, List<LimitProfile> limits) {
    public PluginSettings { limits = List.copyOf(limits); }

    public record OrderSettings(Duration defaultDuration, Duration minimumDuration, Duration maximumDuration,
                                int expirationBatchSize, int expirationCheckIntervalSeconds,
                                int historyRetentionDays, boolean cancellationAllowed,
                                long maximumItemsPerTransaction, int maximumPendingCollectionEntries) {}

    public record PerformanceSettings(int databaseThreads, int databaseQueueCapacity, int queryTimeoutSeconds,
                                      int shutdownGraceSeconds, int browserPageSize, int searchCacheSeconds,
                                      int summaryCacheMaximum, long guiRefreshCooldownMillis,
                                      int searchesPerMinute, long slowQueryMillis, boolean debugMetrics) {}

    public record Fee(BigDecimal flat, BigDecimal percent) {}

    public record EconomySettings(int currencyScale, RoundingMode roundingMode,
                                  BigDecimal minimumPricePerItem, BigDecimal maximumPricePerItem,
                                  BigDecimal minimumTotalOrderValue, BigDecimal maximumTotalOrderValue,
                                  Fee listingFee, Fee creationTax, Fee sellerTax, Fee cancellationFee,
                                  boolean commandFallbackEnabled, String withdrawCommand, String depositCommand) {}

    public record NotificationSettings(boolean loginSummary, boolean chat, boolean actionBar,
                                       boolean title, boolean sound) {}
}
