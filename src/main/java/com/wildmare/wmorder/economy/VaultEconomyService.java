package com.wildmare.wmorder.economy;

import com.wildmare.wmorder.util.MoneyMath;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Objects;

public final class VaultEconomyService implements EconomyService {
    private final Economy economy;
    private final MoneyMath money;
    public VaultEconomyService(Economy economy,MoneyMath money){this.economy=Objects.requireNonNull(economy);this.money=money;}
    @Override public EconomyResult withdraw(OfflinePlayer player,BigDecimal amount){assertMain();BigDecimal normalized=money.normalize(amount);EconomyResponse response=economy.withdrawPlayer(player,normalized.doubleValue());return convert(normalized,response);}
    @Override public EconomyResult deposit(OfflinePlayer player,BigDecimal amount){assertMain();BigDecimal normalized=money.normalize(amount);EconomyResponse response=economy.depositPlayer(player,normalized.doubleValue());return convert(normalized,response);}
    @Override public BigDecimal balance(OfflinePlayer player){assertMain();double value=economy.getBalance(player);if(!Double.isFinite(value))return BigDecimal.ZERO;return money.normalize(BigDecimal.valueOf(value));}
    @Override public String providerName(){return economy.getName();}
    private EconomyResult convert(BigDecimal requested,EconomyResponse response){double balance=response.balance;BigDecimal safeBalance=Double.isFinite(balance)?money.normalize(BigDecimal.valueOf(balance)):BigDecimal.ZERO;return new EconomyResult(response.transactionSuccess(),requested,safeBalance,response.errorMessage==null?response.type.name():response.errorMessage);}
    private void assertMain(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Vault economy calls must run on the server thread");}
}
