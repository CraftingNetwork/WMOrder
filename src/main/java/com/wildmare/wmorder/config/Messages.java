package com.wildmare.wmorder.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Messages {
    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(ConfigManager configs) { this.configs = configs; }

    public Component render(String key, Map<String, ?> placeholders) {
        YamlConfiguration c = configs.messagesConfig();
        String template = c.getString(key, "<red>Missing message: " + key + "</red>");
        String prefix = c.getString("prefix", "");
        return miniMessage.deserialize(prefix + template, resolvers(placeholders));
    }

    public Component renderRaw(String raw, Map<String, ?> placeholders) {
        return miniMessage.deserialize(raw == null ? "" : raw, resolvers(placeholders));
    }

    public List<Component> renderList(String key, Map<String, ?> placeholders) {
        List<Component> result = new ArrayList<>();
        for (String line : configs.messagesConfig().getStringList(key)) {
            result.add(miniMessage.deserialize(line, resolvers(placeholders)));
        }
        return result;
    }

    public void send(CommandSender sender, String key) { sender.sendMessage(render(key, Map.of())); }
    public void send(CommandSender sender, String key, Map<String, ?> placeholders) { sender.sendMessage(render(key, placeholders)); }

    private TagResolver resolvers(Map<String, ?> values) {
        TagResolver.Builder builder = TagResolver.builder();
        values.forEach((key, value) -> builder.resolver(Placeholder.unparsed(key, String.valueOf(value))));
        return builder.build();
    }
}
