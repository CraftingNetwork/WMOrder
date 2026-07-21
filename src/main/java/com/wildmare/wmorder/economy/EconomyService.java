package com.wildmare.wmorder.economy;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

public interface EconomyService {
    EconomyResult withdraw(OfflinePlayer player, BigDecimal amount);
    EconomyResult deposit(OfflinePlayer player, BigDecimal amount);
    BigDecimal balance(OfflinePlayer player);
    String providerName();
}
