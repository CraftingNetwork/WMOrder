package com.wildmare.wmorder.item;

import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.config.ItemSettings;
import com.wildmare.wmorder.util.Hashing;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;

public final class ItemMatcher {
    private final ConfigManager configs;
    private final ItemSerializer serializer;
    private final ItemNormalizer normalizer;

    public ItemMatcher(ConfigManager configs, ItemSerializer serializer, ItemNormalizer normalizer) {
        this.configs = configs; this.serializer = serializer; this.normalizer = normalizer;
    }

    public ItemIdentity identity(ItemStack source) {
        if (source == null || source.getType().isAir()) throw new IllegalArgumentException("air");
        ItemStack normalized = normalizer.normalized(source, configs.itemSettings());
        byte[] canonical = configs.itemSettings().matchingMode() == MatchingMode.MATERIAL_ONLY
                ? normalized.getType().name().getBytes(StandardCharsets.UTF_8)
                : serializer.serialize(normalized);
        ItemStack stored = source.clone(); stored.setAmount(1);
        byte[] serialized = serializer.serialize(stored);
        return new ItemIdentity(source.getType().name(), Hashing.sha256(canonical), serialized, displayName(source));
    }

    public boolean matches(ItemStack requested, ItemStack candidate) {
        if (requested == null || candidate == null || requested.getType().isAir() || candidate.getType().isAir()) return false;
        ItemSettings settings = configs.itemSettings();
        boolean materialEqual = requested.getType() == candidate.getType();
        if (!materialEqual) return false;
        if (settings.matchingMode() == MatchingMode.MATERIAL_ONLY) {
            return MatchDecision.matches(settings.matchingMode(), true, false, false);
        }
        ItemStack a = normalizer.normalized(requested, settings);
        ItemStack b = normalizer.normalized(candidate, settings);
        boolean similar = a.isSimilar(b);
        boolean fingerprint = settings.matchingMode() == MatchingMode.EXACT
                && Hashing.sha256(serializer.serialize(a)).equals(Hashing.sha256(serializer.serialize(b)));
        return MatchDecision.matches(settings.matchingMode(), true, similar, fingerprint);
    }

    public boolean matchesFingerprint(String expectedFingerprint, ItemStack candidate) {
        if (candidate == null || candidate.getType().isAir()) return false;
        return identity(candidate).fingerprint().equals(expectedFingerprint);
    }

    public String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().displayName() != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
            if (!plain.isBlank()) return plain;
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder(name.length());
        boolean upper = true;
        for (char c : name.toCharArray()) {
            if (upper && Character.isLetter(c)) { result.append(Character.toUpperCase(c)); upper = false; }
            else { result.append(c); upper = c == ' '; }
        }
        return result.toString();
    }

    public Material material(byte[] blob) { return serializer.deserialize(blob).getType(); }
}
