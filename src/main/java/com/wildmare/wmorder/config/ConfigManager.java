package com.wildmare.wmorder.config;

import com.wildmare.wmorder.item.MatchingMode;
import com.wildmare.wmorder.permission.LimitProfile;
import com.wildmare.wmorder.util.DurationParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;

public final class ConfigManager {
    private static final List<String> FILES = List.of("config.yml", "database.yml", "messages.yml", "gui.yml", "categories.yml", "items.yml");
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile PluginSettings settings;
    private volatile DatabaseSettings databaseSettings;
    private volatile ItemSettings itemSettings;
    private volatile CategoryRegistry categories;
    private volatile YamlConfiguration messages;
    private volatile YamlConfiguration gui;

    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }

    public void initialize() {
        for (String file : FILES) {
            File target = new File(plugin.getDataFolder(), file);
            if (!target.exists()) plugin.saveResource(file, false);
        }
        reloadAll();
    }

    public synchronized void reloadAll() {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration database = load("database.yml");
        this.messages = load("messages.yml");
        this.gui = load("gui.yml");
        this.settings = readPluginSettings(config);
        this.databaseSettings = readDatabaseSettings(database);
        this.itemSettings = readItemSettings(load("items.yml"));
        this.categories = readCategories(load("categories.yml"));
    }

    public synchronized void reloadSafe() {
        YamlConfiguration config = load("config.yml");
        this.messages = load("messages.yml");
        this.gui = load("gui.yml");
        this.settings = readPluginSettings(config);
        this.itemSettings = readItemSettings(load("items.yml"));
        this.categories = readCategories(load("categories.yml"));
    }

    private YamlConfiguration load(String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    private PluginSettings readPluginSettings(YamlConfiguration c) {
        int scale = clamp(c.getInt("economy.currency-scale", 2), 0, 8);
        RoundingMode rounding;
        try { rounding = RoundingMode.valueOf(c.getString("economy.rounding-mode", "HALF_UP").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { rounding = RoundingMode.HALF_UP; }
        PluginSettings.Fee listing = fee(c, "economy.listing-fee");
        PluginSettings.Fee creation = fee(c, "economy.creation-tax");
        PluginSettings.Fee seller = fee(c, "economy.seller-tax");
        PluginSettings.Fee cancellation = fee(c, "economy.cancellation-fee");
        PluginSettings.EconomySettings economy = new PluginSettings.EconomySettings(
                scale, rounding,
                decimal(c, "economy.minimum-price-per-item", "0.01"),
                decimal(c, "economy.maximum-price-per-item", "1000000000"),
                decimal(c, "economy.minimum-total-order-value", "1"),
                decimal(c, "economy.maximum-total-order-value", "10000000000"),
                listing, creation, seller, cancellation,
                c.getBoolean("economy.command-fallback.enabled", false),
                c.getString("economy.command-fallback.withdraw-command", ""),
                c.getString("economy.command-fallback.deposit-command", "")
        );
        PluginSettings.OrderSettings orders = new PluginSettings.OrderSettings(
                duration(c, "orders.default-duration", "7d"), duration(c, "orders.minimum-duration", "1h"),
                duration(c, "orders.maximum-duration", "30d"),
                clamp(c.getInt("orders.expiration-batch-size", 100), 1, 5000),
                clamp(c.getInt("orders.expiration-check-interval-seconds", 30), 5, 3600),
                clamp(c.getInt("orders.history-retention-days", 90), 1, 3650),
                c.getBoolean("orders.cancellation-allowed", true),
                Math.max(1, c.getLong("orders.maximum-items-per-transaction", 2304)),
                clamp(c.getInt("orders.maximum-pending-collection-entries", 500), 1, 10000)
        );
        PluginSettings.PerformanceSettings performance = new PluginSettings.PerformanceSettings(
                clamp(c.getInt("performance.database-threads", 4), 1, 32),
                clamp(c.getInt("performance.database-queue-capacity", 2000), 100, 100000),
                clamp(c.getInt("performance.query-timeout-seconds", 10), 1, 120),
                clamp(c.getInt("performance.shutdown-grace-seconds", 10), 1, 60),
                clamp(c.getInt("performance.browser-page-size", 45), 9, 45),
                clamp(c.getInt("performance.search-cache-seconds", 10), 0, 300),
                clamp(c.getInt("performance.summary-cache-maximum", 5000), 100, 100000),
                Math.max(250, c.getLong("performance.gui-refresh-cooldown-millis", 1000)),
                clamp(c.getInt("performance.searches-per-minute", 20), 1, 600),
                Math.max(10, c.getLong("performance.slow-query-millis", 250)),
                c.getBoolean("performance.debug-metrics", false)
        );
        PluginSettings.NotificationSettings notifications = new PluginSettings.NotificationSettings(
                c.getBoolean("notifications.login-summary", true), c.getBoolean("notifications.chat", true),
                c.getBoolean("notifications.action-bar", false), c.getBoolean("notifications.title", false),
                c.getBoolean("notifications.sound", true));
        List<LimitProfile> limits = new ArrayList<>();
        ConfigurationSection section = c.getConfigurationSection("limits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String base = "limits." + key + ".";
                limits.add(new LimitProfile(key, c.getString(base + "permission", "wmorder.limit." + key),
                        c.getInt(base + "priority", 0), Math.max(1, c.getInt(base + "max-active-orders", 5)),
                        Math.max(1, c.getLong(base + "max-quantity-per-order", 2304)),
                        decimal(c, base + "max-total-value", "10000000"), duration(c, base + "duration", "7d"),
                        Math.max(0, c.getInt(base + "creation-cooldown-seconds", 5)),
                        Math.max(0, c.getInt(base + "fulfillment-cooldown-seconds", 2)),
                        decimal(c, base + "tax-reduction-percent", "0").max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)),
                        c.getBoolean(base + "listing-fee-exempt", false)));
            }
        }
        if (limits.isEmpty()) limits.add(new LimitProfile("default", "wmorder.limit.default", 0, 5, 2304,
                new BigDecimal("10000000"), orders.defaultDuration(), 5, 2, BigDecimal.ZERO, false));
        limits.sort(Comparator.comparingInt(LimitProfile::priority).reversed());
        return new PluginSettings(c.getString("server-id", "default"), c.getString("locale", "en-US"),
                orders, performance, economy, notifications, limits);
    }

    private DatabaseSettings readDatabaseSettings(YamlConfiguration c) {
        DatabaseSettings.DatabaseType type;
        try { type = DatabaseSettings.DatabaseType.valueOf(c.getString("type", "SQLITE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { type = DatabaseSettings.DatabaseType.SQLITE; }
        return new DatabaseSettings(type, c.getString("sqlite.file", "wmorder.db"),
                c.getBoolean("sqlite.wal", true), Math.max(0, c.getInt("sqlite.busy-timeout-millis", 5000)),
                c.getString("mysql.host", "127.0.0.1"), c.getInt("mysql.port", 3306),
                c.getString("mysql.database", "minecraft"), c.getString("mysql.username", "wmorder"),
                c.getString("mysql.password", ""), c.getString("mysql.parameters", ""),
                clamp(c.getInt("pool.maximum-size", 10), 1, 64), clamp(c.getInt("pool.minimum-idle", 1), 0, 64),
                Math.max(1000, c.getLong("pool.connection-timeout-millis", 10000)),
                Math.max(1000, c.getLong("pool.validation-timeout-millis", 5000)),
                Math.max(10000, c.getLong("pool.idle-timeout-millis", 600000)),
                Math.max(30000, c.getLong("pool.max-lifetime-millis", 1800000)));
    }

    private ItemSettings readItemSettings(YamlConfiguration c) {
        Set<Material> blacklist = materials(c.getStringList("blacklist"));
        Set<Material> whitelist = materials(c.getStringList("whitelist"));
        Set<Integer> customModels = new HashSet<>();
        for (Integer value : c.getIntegerList("custom-model-data-blacklist")) customModels.add(value);
        ItemSettings.IgnoreRules ignore = new ItemSettings.IgnoreRules(
                c.getBoolean("matching-ignore.custom-model-data", false), c.getBoolean("matching-ignore.display-name", false),
                c.getBoolean("matching-ignore.lore", false), c.getBoolean("matching-ignore.enchantments", false),
                c.getBoolean("matching-ignore.attributes", false), c.getBoolean("matching-ignore.damage", false),
                c.getBoolean("matching-ignore.persistent-data", false), c.getBoolean("matching-ignore.container-contents", false));
        return new ItemSettings(MatchingMode.parse(c.getString("matching-mode", "EXACT")),
                c.getBoolean("allow-damaged-items", false), c.getBoolean("allow-unstackable-items", true),
                c.getBoolean("allow-container-items", true), c.getBoolean("allow-shulker-box-orders", true),
                c.getBoolean("scan-shulker-contents", false), clamp(c.getInt("maximum-container-depth", 1), 0, 3),
                clamp(c.getInt("maximum-serialized-item-bytes", 131072), 1024, 1048576),
                clamp(c.getInt("maximum-item-name-length", 128), 1, 1024),
                clamp(c.getInt("maximum-lore-lines", 64), 0, 1024),
                c.getBoolean("persistent-data.allowed", true), new HashSet<>(c.getStringList("persistent-data.ignored-namespaced-keys")),
                ignore, blacklist, c.getBoolean("whitelist-mode", false), whitelist, customModels,
                new HashSet<>(c.getStringList("restricted-enchantments")));
    }

    private CategoryRegistry readCategories(YamlConfiguration c) {
        List<CategoryRegistry.Category> result = new ArrayList<>();
        ConfigurationSection section = c.getConfigurationSection("categories");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "categories." + id + ".";
                String rawName = c.getString(path + "name", id);
                Material icon = Material.matchMaterial(c.getString(path + "icon", "CHEST"));
                if (icon == null) icon = Material.CHEST;
                result.add(new CategoryRegistry.Category(id, rawName, miniMessage.deserialize(rawName), icon,
                        materials(c.getStringList(path + "materials"))));
            }
        }
        return new CategoryRegistry(result);
    }

    private Set<Material> materials(List<String> names) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) result.add(material);
            else plugin.getLogger().warning("Unknown material in configuration: " + name);
        }
        return result;
    }

    private PluginSettings.Fee fee(YamlConfiguration c, String path) {
        return new PluginSettings.Fee(decimal(c, path + ".flat", "0"), decimal(c, path + ".percent", "0"));
    }
    private BigDecimal decimal(YamlConfiguration c, String path, String fallback) {
        try { return new BigDecimal(Objects.requireNonNullElse(c.getString(path), fallback)); }
        catch (NumberFormatException exception) {
            plugin.getLogger().log(Level.WARNING, "Invalid decimal at " + path + "; using " + fallback, exception);
            return new BigDecimal(fallback);
        }
    }
    private java.time.Duration duration(YamlConfiguration c, String path, String fallback) {
        try { return DurationParser.parse(c.getString(path, fallback)); }
        catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid duration at " + path + "; using " + fallback);
            return DurationParser.parse(fallback);
        }
    }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public PluginSettings settings() { return settings; }
    public DatabaseSettings databaseSettings() { return databaseSettings; }
    public ItemSettings itemSettings() { return itemSettings; }
    public CategoryRegistry categories() { return categories; }
    public YamlConfiguration messagesConfig() { return messages; }
    public YamlConfiguration guiConfig() { return gui; }
}
