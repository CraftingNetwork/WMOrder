package com.wildmare.wmorder.order.model;

import java.math.BigDecimal;

public record MoneyBreakdown(BigDecimal gross, BigDecimal flatFee, BigDecimal percentageFee,
                             BigDecimal totalFee, BigDecimal netOrDeposit) {
}
