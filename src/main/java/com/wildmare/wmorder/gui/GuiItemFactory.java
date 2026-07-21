package com.wildmare.wmorder.gui;

import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class GuiItemFactory {
    private final ConfigManager configs;private final Messages messages;
    public GuiItemFactory(ConfigManager configs,Messages messages){this.configs=configs;this.messages=messages;}
    public ItemStack button(String id,Map<String,?> placeholders){
        String base="buttons."+id;ConfigurationSection section=configs.guiConfig().getConfigurationSection(base);Material material=Material.BARRIER;
        if(section!=null){Material found=Material.matchMaterial(section.getString("material","BARRIER"));if(found!=null)material=found;}
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();String name=section==null?id:section.getString("name",id);meta.displayName(messages.renderRaw(name,placeholders));
        List<Component> lore=new ArrayList<>();if(section!=null)for(String line:section.getStringList("lore"))lore.add(messages.renderRaw(line,placeholders));if(!lore.isEmpty())meta.lore(lore);
        int model=section==null?0:section.getInt("custom-model-data",0);if(model>0)meta.setCustomModelData(model);item.setItemMeta(meta);return item;
    }
    public void fill(org.bukkit.inventory.Inventory inventory){ItemStack filler=button("filler",Map.of());for(int i=0;i<inventory.getSize();i++)if(inventory.getItem(i)==null)inventory.setItem(i,filler);}
}
