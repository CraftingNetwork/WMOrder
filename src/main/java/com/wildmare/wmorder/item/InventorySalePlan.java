package com.wildmare.wmorder.item;

import java.util.List;

public record InventorySalePlan(long quantity, List<Mutation> mutations) {
    public InventorySalePlan { mutations=List.copyOf(mutations); }
    public record Mutation(int inventorySlot, int nestedSlot, int amount, String expectedOuterFingerprint, String expectedItemFingerprint) {
        public boolean nested() { return nestedSlot >= 0; }
    }
}
