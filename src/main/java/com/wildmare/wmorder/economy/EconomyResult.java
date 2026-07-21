package com.wildmare.wmorder.economy;

import java.math.BigDecimal;

public record EconomyResult(boolean success, BigDecimal amount, BigDecimal balance, String response) {
}
