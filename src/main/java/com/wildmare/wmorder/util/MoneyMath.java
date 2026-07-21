package com.wildmare.wmorder.util;

import com.wildmare.wmorder.order.model.MoneyBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {
    private final int scale;
    private final RoundingMode roundingMode;

    public MoneyMath(int scale, RoundingMode roundingMode) {
        if (scale < 0 || scale > 8) throw new IllegalArgumentException("currency scale must be 0..8");
        this.scale = scale;
        this.roundingMode = roundingMode;
    }

    public BigDecimal normalize(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("money is null");
        return value.setScale(scale, roundingMode);
    }

    public BigDecimal multiply(BigDecimal price, long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("negative quantity");
        return normalize(price.multiply(BigDecimal.valueOf(quantity)));
    }

    public BigDecimal percentage(BigDecimal base, BigDecimal percent) {
        return normalize(base.multiply(percent).divide(BigDecimal.valueOf(100), scale + 6, roundingMode));
    }

    public MoneyBreakdown deposit(BigDecimal gross, BigDecimal flat, BigDecimal percent, BigDecimal reductionPercent,
                                  boolean flatExempt) {
        BigDecimal normalizedGross = normalize(gross);
        BigDecimal effectivePercent = percent.multiply(BigDecimal.valueOf(100).subtract(reductionPercent))
                .divide(BigDecimal.valueOf(100), scale + 6, roundingMode).max(BigDecimal.ZERO);
        BigDecimal flatFee = flatExempt ? normalize(BigDecimal.ZERO) : normalize(flat);
        BigDecimal pctFee = percentage(normalizedGross, effectivePercent);
        BigDecimal totalFee = normalize(flatFee.add(pctFee));
        return new MoneyBreakdown(normalizedGross, flatFee, pctFee, totalFee, normalize(normalizedGross.add(totalFee)));
    }

    public MoneyBreakdown payout(BigDecimal gross, BigDecimal flat, BigDecimal percent, BigDecimal reductionPercent) {
        BigDecimal normalizedGross = normalize(gross);
        BigDecimal effectivePercent = percent.multiply(BigDecimal.valueOf(100).subtract(reductionPercent))
                .divide(BigDecimal.valueOf(100), scale + 6, roundingMode).max(BigDecimal.ZERO);
        BigDecimal flatFee = normalize(flat);
        BigDecimal pctFee = percentage(normalizedGross, effectivePercent);
        BigDecimal totalFee = normalize(flatFee.add(pctFee).min(normalizedGross));
        return new MoneyBreakdown(normalizedGross, flatFee, pctFee, totalFee, normalize(normalizedGross.subtract(totalFee)));
    }

    public int scale() { return scale; }
    public RoundingMode roundingMode() { return roundingMode; }
}
