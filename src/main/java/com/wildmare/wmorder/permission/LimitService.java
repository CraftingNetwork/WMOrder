package com.wildmare.wmorder.permission;

import com.wildmare.wmorder.config.ConfigManager;
import org.bukkit.command.CommandSender;

public final class LimitService {
    private final ConfigManager configs;
    public LimitService(ConfigManager configs) { this.configs = configs; }

    public LimitProfile resolve(CommandSender sender) {
        for (LimitProfile profile : configs.settings().limits()) {
            if (sender.hasPermission(profile.permission()) || profile.name().equalsIgnoreCase("default")) return profile;
        }
        return configs.settings().limits().get(configs.settings().limits().size() - 1);
    }
}
