package com.wildmare.wmorder.order.model;

import java.util.Set;
import java.util.UUID;

public record OrderQuery(
        String search,
        String category,
        String material,
        UUID buyerUuid,
        Set<OrderStatus> statuses,
        OrderSort sort,
        boolean fulfillableOnly,
        int page,
        int pageSize
) {
    public OrderQuery {
        search = search == null ? "" : search.trim();
        category = category == null || category.isBlank() ? null : category.trim();
        material = material == null || material.isBlank() ? null : material.trim().toUpperCase();
        statuses = statuses == null ? Set.of(OrderStatus.ACTIVE, OrderStatus.PARTIALLY_FILLED) : Set.copyOf(statuses);
        sort = sort == null ? OrderSort.NEWEST : sort;
        page = Math.max(0, page);
        pageSize = Math.max(1, Math.min(pageSize, 100));
    }

    public static OrderQuery browser(int page, int pageSize) {
        return new OrderQuery("", null, null, null,
                Set.of(OrderStatus.ACTIVE, OrderStatus.PARTIALLY_FILLED), OrderSort.NEWEST, false, page, pageSize);
    }

    public OrderQuery withPage(int newPage) {
        return new OrderQuery(search, category, material, buyerUuid, statuses, sort, fulfillableOnly, newPage, pageSize);
    }

    public OrderQuery withSearch(String newSearch) {
        return new OrderQuery(newSearch, category, material, buyerUuid, statuses, sort, fulfillableOnly, 0, pageSize);
    }
}
