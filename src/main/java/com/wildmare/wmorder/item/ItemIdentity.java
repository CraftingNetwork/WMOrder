package com.wildmare.wmorder.item;

import java.util.Arrays;
import java.util.Objects;

public record ItemIdentity(String material, String fingerprint, byte[] serialized, String displayName) {
    public ItemIdentity {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(serialized, "serialized");
        Objects.requireNonNull(displayName, "displayName");
        serialized = Arrays.copyOf(serialized, serialized.length);
    }
    @Override public byte[] serialized() { return Arrays.copyOf(serialized, serialized.length); }
}
