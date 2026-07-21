package com.wildmare.wmorder.config;

import com.wildmare.wmorder.item.MatchingMode;
import org.bukkit.Material;

import java.util.Set;

public record ItemSettings(MatchingMode matchingMode, boolean allowDamagedItems, boolean allowUnstackableItems,
                           boolean allowContainerItems, boolean allowShulkerBoxOrders,
                           boolean scanShulkerContents, int maximumContainerDepth,
                           int maximumSerializedItemBytes, int maximumItemNameLength,
                           int maximumLoreLines, boolean persistentDataAllowed,
                           Set<String> ignoredPersistentKeys, IgnoreRules ignoreRules,
                           Set<Material> blacklist, boolean whitelistMode, Set<Material> whitelist,
                           Set<Integer> customModelDataBlacklist, Set<String> restrictedEnchantments) {
    public ItemSettings {
        ignoredPersistentKeys = Set.copyOf(ignoredPersistentKeys);
        blacklist = Set.copyOf(blacklist);
        whitelist = Set.copyOf(whitelist);
        customModelDataBlacklist = Set.copyOf(customModelDataBlacklist);
        restrictedEnchantments = Set.copyOf(restrictedEnchantments);
    }

    public record IgnoreRules(boolean customModelData, boolean displayName, boolean lore,
                              boolean enchantments, boolean attributes, boolean damage,
                              boolean persistentData, boolean containerContents) {}
}
