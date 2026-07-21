package com.wildmare.wmorder.order.model;

import java.util.List;

public record OrderPage(List<OrderSummary> entries, int page, int pageSize, boolean hasNext) {
    public OrderPage {
        entries = List.copyOf(entries);
    }
}
