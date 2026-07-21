package com.wildmare.wmorder.order.service;

import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.config.PluginSettings;
import com.wildmare.wmorder.order.model.MoneyBreakdown;
import com.wildmare.wmorder.permission.LimitProfile;
import com.wildmare.wmorder.util.MoneyMath;

import java.math.BigDecimal;

public final class PriceCalculator {
    private final ConfigManager configs;
    private final MoneyMath money;
    public PriceCalculator(ConfigManager configs,MoneyMath money){this.configs=configs;this.money=money;}

    public MoneyBreakdown creation(BigDecimal gross,LimitProfile profile){
        PluginSettings.EconomySettings e=configs.settings().economy();
        BigDecimal listingFlat=profile.listingFeeExempt()?BigDecimal.ZERO:e.listingFee().flat();
        BigDecimal flat=listingFlat.add(e.creationTax().flat());
        BigDecimal effectiveFactor=BigDecimal.valueOf(100).subtract(profile.taxReductionPercent()).max(BigDecimal.ZERO);
        BigDecimal listingPct=e.listingFee().percent().multiply(effectiveFactor).divide(BigDecimal.valueOf(100),money.scale()+6,money.roundingMode());
        BigDecimal creationPct=e.creationTax().percent().multiply(effectiveFactor).divide(BigDecimal.valueOf(100),money.scale()+6,money.roundingMode());
        BigDecimal percentage=money.percentage(gross,listingPct.add(creationPct));
        BigDecimal flatNormalized=money.normalize(flat);BigDecimal total=money.normalize(flatNormalized.add(percentage));
        return new MoneyBreakdown(money.normalize(gross),flatNormalized,percentage,total,money.normalize(gross.add(total)));
    }

    public MoneyBreakdown sellerPayout(BigDecimal gross,LimitProfile profile){
        PluginSettings.Fee fee=configs.settings().economy().sellerTax();
        return money.payout(gross,fee.flat(),fee.percent(),profile.taxReductionPercent());
    }

    public MoneyBreakdown cancellationRefund(BigDecimal reserved,LimitProfile profile){
        PluginSettings.Fee fee=configs.settings().economy().cancellationFee();
        MoneyBreakdown payout=money.payout(reserved,fee.flat(),fee.percent(),profile.taxReductionPercent());
        return new MoneyBreakdown(payout.gross(),payout.flatFee(),payout.percentageFee(),payout.totalFee(),payout.netOrDeposit());
    }

    public MoneyBreakdown expirationRefund(BigDecimal reserved){
        BigDecimal normalized=money.normalize(reserved);
        return new MoneyBreakdown(normalized,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,normalized);
    }
}
