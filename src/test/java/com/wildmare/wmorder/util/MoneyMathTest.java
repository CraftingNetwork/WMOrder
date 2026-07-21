package com.wildmare.wmorder.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static org.assertj.core.api.Assertions.*;

class MoneyMathTest {
    private final MoneyMath money = new MoneyMath(2, RoundingMode.HALF_UP);

    @Test void normalizesAndMultipliesWithoutFloatingPoint() {
        assertThat(money.normalize(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(money.multiply(new BigDecimal("12.345"), 3)).isEqualByComparingTo("37.04");
    }

    @Test void calculatesDepositFeesAndPermissionReduction() {
        var result = money.deposit(new BigDecimal("100.00"), new BigDecimal("2.00"), new BigDecimal("10"), new BigDecimal("25"), false);
        assertThat(result.totalFee()).isEqualByComparingTo("9.50");
        assertThat(result.netOrDeposit()).isEqualByComparingTo("109.50");
    }

    @Test void sellerFeesNeverExceedGross() {
        var result = money.payout(new BigDecimal("5.00"), new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(result.totalFee()).isEqualByComparingTo("5.00");
        assertThat(result.netOrDeposit()).isZero();
    }

    @Test void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> money.multiply(BigDecimal.ONE, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
