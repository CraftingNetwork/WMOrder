package com.wildmare.wmorder.item;

import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.config.ItemSettings;
import com.wildmare.wmorder.util.OperationResult;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemRestrictionService {
    private final ConfigManager configs;
    private final ItemSerializer serializer;
    public ItemRestrictionService(ConfigManager configs, ItemSerializer serializer) { this.configs=configs; this.serializer=serializer; }

    public OperationResult<Void> validate(ItemStack item) {
        if (item == null || item.getType().isAir()) return OperationResult.failure("air", "Air cannot be ordered");
        ItemSettings s=configs.itemSettings();
        if (s.whitelistMode() && !s.whitelist().contains(item.getType())) return OperationResult.failure("not_whitelisted", item.getType().name());
        if (s.blacklist().contains(item.getType())) return OperationResult.failure("blacklisted", item.getType().name());
        if (!s.allowUnstackableItems() && item.getMaxStackSize()==1) return OperationResult.failure("unstackable", "Unstackable items are disabled");
        ItemMeta meta=item.getItemMeta();
        if (meta != null) {
            if (!s.allowDamagedItems() && meta instanceof Damageable damageable && damageable.getDamage()>0)
                return OperationResult.failure("damaged", "Damaged items are disabled");
            if (meta.hasCustomModelData() && s.customModelDataBlacklist().contains(meta.getCustomModelData()))
                return OperationResult.failure("custom_model_data", "Custom model data is blacklisted");
            if (!s.persistentDataAllowed() && !meta.getPersistentDataContainer().getKeys().isEmpty())
                return OperationResult.failure("persistent_data", "Persistent data items are disabled");
            if (meta.hasDisplayName() && meta.displayName()!=null) {
                String name=PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                if (name.length()>s.maximumItemNameLength()) return OperationResult.failure("name_too_long", "Item name is too long");
            }
            if (meta.hasLore() && meta.lore()!=null && meta.lore().size()>s.maximumLoreLines())
                return OperationResult.failure("lore_too_long", "Too many lore lines");
            for (Enchantment enchantment:meta.getEnchants().keySet()) {
                if (s.restrictedEnchantments().contains(enchantment.getKey().toString()) || s.restrictedEnchantments().contains(enchantment.getKey().getKey()))
                    return OperationResult.failure("enchantment", enchantment.getKey().toString());
            }
            if (meta instanceof BlockStateMeta block && block.getBlockState() instanceof Container) {
                if (!s.allowContainerItems()) return OperationResult.failure("container", "Container items are disabled");
                if (!s.allowShulkerBoxOrders() && item.getType().name().endsWith("SHULKER_BOX")) return OperationResult.failure("shulker", "Shulker orders are disabled");
            }
        }
        byte[] bytes=serializer.serialize(item);
        if(bytes.length>s.maximumSerializedItemBytes())return OperationResult.failure("item_too_large", "Serialized item size is "+bytes.length+" bytes");
        return OperationResult.success(null);
    }
}
