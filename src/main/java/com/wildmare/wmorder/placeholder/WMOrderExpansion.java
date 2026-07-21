package com.wildmare.wmorder.placeholder;

import com.wildmare.wmorder.database.model.PlayerStatistics;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

public final class WMOrderExpansion extends PlaceholderExpansion {
    private final StatsCache cache;private final String version;
    public WMOrderExpansion(StatsCache cache,String version){this.cache=cache;this.version=version;}
    @Override public @NotNull String getIdentifier(){return "wmorder";}
    @Override public @NotNull String getAuthor(){return "WildMare";}
    @Override public @NotNull String getVersion(){return version;}
    @Override public boolean persist(){return true;}
    @Override public @Nullable String onRequest(OfflinePlayer player,@NotNull String params){
        if(player==null)return "0";PlayerStatistics s=cache.get(player.getUniqueId());return switch(params.toLowerCase()){
            case "active_orders"->Long.toString(s.activeOrders());case "completed_orders"->Long.toString(s.completedOrders());case "pending_collection"->Long.toString(s.pendingCollection());
            case "total_spent"->format(s.totalSpent());case "total_earned"->format(s.totalEarned());case "highest_order"->format(s.highestOrder());case "market_volume"->format(s.marketVolume());default->null;};
    }
    private String format(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
}
