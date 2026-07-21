package com.wildmare.wmorder.item;

import com.wildmare.wmorder.config.ItemSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public final class ItemNormalizer {
    public ItemStack normalized(ItemStack source, ItemSettings settings) {
        ItemStack item = source.clone();
        item.setAmount(1);
        if (settings.matchingMode() == MatchingMode.MATERIAL_ONLY) {
            return new ItemStack(item.getType(), 1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        ItemSettings.IgnoreRules ignore = settings.ignoreRules();
        if (ignore.displayName()) meta.displayName(null);
        if (ignore.lore()) meta.lore(null);
        if (ignore.customModelData() && meta.hasCustomModelData()) meta.setCustomModelData(null);
        if (ignore.enchantments()) new ArrayList<>(meta.getEnchants().keySet()).forEach(meta::removeEnchant);
        if (ignore.attributes()) meta.setAttributeModifiers(null);
        if (ignore.damage() && meta instanceof Damageable damageable) damageable.setDamage(0);
        if (ignore.persistentData()) {
            for (NamespacedKey key : new ArrayList<>(meta.getPersistentDataContainer().getKeys())) {
                meta.getPersistentDataContainer().remove(key);
            }
        } else {
            for (NamespacedKey key : new ArrayList<>(meta.getPersistentDataContainer().getKeys())) {
                if (settings.ignoredPersistentKeys().contains(key.toString())) meta.getPersistentDataContainer().remove(key);
            }
        }
        if (ignore.containerContents() && meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof Container container) {
            container.getInventory().clear();
            blockStateMeta.setBlockState(container);
        }
        item.setItemMeta(meta);
        return item;
    }
}
