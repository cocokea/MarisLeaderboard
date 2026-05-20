package com.maris7.leaderboard.config;

import com.maris7.leaderboard.model.LeaderboardCategory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class PluginConfig {
    private final JavaPlugin plugin;
    private final FileConfiguration soundsConfig;
    private final List<LeaderboardCategory> categories;
    private final Map<String, LeaderboardCategory> categoriesByKey;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        File soundsFile = plugin.getDataFolder().toPath().resolve("sounds.yml").toFile();
        if (!soundsFile.exists()) {
            plugin.saveResource("sounds.yml", false);
        }
        this.soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);
        this.categories = loadCategories();
        this.categoriesByKey = new LinkedHashMap<>();
        for (LeaderboardCategory category : categories) {
            categoriesByKey.put(category.key(), category);
        }
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    private List<LeaderboardCategory> loadCategories() {
        ConfigurationSection section = c().getConfigurationSection("settings.placeholders");
        if (section == null) {
            return List.of(
                    new LeaderboardCategory("money", "Money", true, "%vault_eco_balance%"),
                    new LeaderboardCategory("shards", "Shards", true, "%shards_value%"),
                    new LeaderboardCategory("kills", "Kills", false, "%statistic_player_kills%"),
                    new LeaderboardCategory("deaths", "Deaths", false, "%statistic_deaths%"),
                    new LeaderboardCategory("playtime", "Playtime", false, "%plan_player_time_total%")
            );
        }
        List<LeaderboardCategory> loaded = new ArrayList<>();
        for (String rawKey : section.getKeys(false)) {
            String key = LeaderboardCategory.normalizeKey(rawKey);
            String placeholder = section.getString(rawKey, "0");
            String displayName = c().getString("settings.category-meta." + key + ".display-name", LeaderboardCategory.defaultDisplayName(key));
            boolean compact = c().getBoolean("settings.category-meta." + key + ".compact", !key.contains("time"));
            loaded.add(new LeaderboardCategory(key, displayName, compact, placeholder));
        }
        return List.copyOf(loaded);
    }

    public JavaPlugin plugin() { return plugin; }
    public List<LeaderboardCategory> categories() { return categories; }
    public Optional<LeaderboardCategory> category(String key) { return Optional.ofNullable(categoriesByKey.get(LeaderboardCategory.normalizeKey(key))); }
    public String mainTitle() { return c().getString("gui.main.title", c().getString("settings.title-main", "LEADERBOARD")); }
    public String boardTitle() { return c().getString("gui.board.title", c().getString("settings.title-board", "{category} - Trang {page}")); }
    public int mainRows() { return c().getInt("gui.main.rows", c().getInt("settings.rows-main", 3)); }
    public int boardRows() { return c().getInt("gui.board.rows", c().getInt("settings.rows-board", 6)); }
    public long refreshTicks() { return c().getLong("settings.refresh-ticks", 600L); }
    public int asyncThreads() { return c().getInt("settings.async-threads", 1); }
    public int maxSearchResults() { return c().getInt("settings.max-search-results", 45); }
    public int cacheTopSize() { return c().getInt("performance.cache-top-size", 200); }
    public int debounceClickMs() { return c().getInt("performance.debounce-click-ms", 150); }
    public boolean updateOnJoin() { return c().getBoolean("performance.update-on-join", true); }
    public boolean updateOnQuit() { return c().getBoolean("performance.update-on-quit", true); }
    public boolean updateOnCommandOpen() { return c().getBoolean("performance.update-on-command-open", true); }
    public String table() { return c().getString("database.table", "maris_leaderboard_data"); }
    public String dbType() { return c().getString("database.type", "SQLITE"); }
    public String sqliteFile() { return c().getString("database.sqlite.file", "plugins/MarisLeaderboard/data.db"); }
    public String mysqlHost() { return c().getString("database.mysql.host", "127.0.0.1"); }
    public int mysqlPort() { return c().getInt("database.mysql.port", 3306); }
    public String mysqlDatabase() { return c().getString("database.mysql.database", "marisleaderboard"); }
    public String mysqlUsername() { return c().getString("database.mysql.username", "root"); }
    public String mysqlPassword() { return c().getString("database.mysql.password", "password"); }
    public String mysqlParameters() { return c().getString("database.mysql.parameters", "useSSL=false"); }
    public String poolName() { return c().getString("database.hikari.pool-name", "MarisLeaderboardPool"); }
    public int maxPoolSize() { return c().getInt("database.hikari.maximum-pool-size", 4); }
    public int minIdle() { return c().getInt("database.hikari.minimum-idle", 1); }
    public long maxLifetime() { return c().getLong("database.hikari.max-lifetime", 1800000L); }
    public long keepAlive() { return c().getLong("database.hikari.keepalive-time", 0L); }
    public long connectionTimeout() { return c().getLong("database.hikari.connection-timeout", 10000L); }
    public long validationTimeout() { return c().getLong("database.hikari.validation-timeout", 5000L); }
    public long leakDetectionThreshold() { return c().getLong("database.hikari.leak-detection-threshold", 0L); }

    public String categoryPlaceholder(LeaderboardCategory category) {
        return category.placeholder();
    }

    public int categorySlot(LeaderboardCategory category) {
        return c().getInt("menu-items.categories." + category.key() + ".slot");
    }

    public Material categoryMaterial(LeaderboardCategory category) {
        return matchMaterial(c().getString("menu-items.categories." + category.key() + ".material", "PAPER"), Material.PAPER);
    }

    public String categoryName(LeaderboardCategory category) {
        return c().getString("menu-items.categories." + category.key() + ".name", category.displayName());
    }

    public List<String> categoryLore(LeaderboardCategory category) {
        return c().getStringList("menu-items.categories." + category.key() + ".lore");
    }

    public int previousSlot() { return c().getInt("menu-items.previous-page.slot", 45); }
    public int nextSlot() { return c().getInt("menu-items.next-page.slot", 53); }
    public int selfSlot() { return c().getInt("menu-items.self.slot", 48); }
    public int refreshSlot() { return c().getInt("menu-items.refresh.slot", 49); }
    public int searchSlot() { return c().getInt("menu-items.search.slot", 50); }
    public Material previousMaterial() { return matchMaterial(c().getString("menu-items.previous-page.material", "ARROW"), Material.ARROW); }
    public Material nextMaterial() { return matchMaterial(c().getString("menu-items.next-page.material", "ARROW"), Material.ARROW); }
    public Material refreshMaterial() { return matchMaterial(c().getString("menu-items.refresh.material", "ANVIL"), Material.ANVIL); }
    public Material searchMaterial() { return matchMaterial(c().getString("menu-items.search.material", "OAK_SIGN"), Material.OAK_SIGN); }
    public String previousName() { return c().getString("menu-items.previous-page.name"); }
    public String nextName() { return c().getString("menu-items.next-page.name"); }
    public String refreshName() { return c().getString("menu-items.refresh.name", "&#00FFAA{category}"); }
    public String searchName() { return c().getString("menu-items.search.name"); }
    public List<String> previousLore() { return c().getStringList("menu-items.previous-page.lore"); }
    public List<String> nextLore() { return c().getStringList("menu-items.next-page.lore"); }
    public List<String> refreshLore() { return c().getStringList("menu-items.refresh.lore"); }
    public List<String> searchLore() { return c().getStringList("menu-items.search.lore"); }
    public String entryName() { return c().getString("menu-items.entry.name"); }
    public List<String> entryLore() { return c().getStringList("menu-items.entry.lore"); }
    public String selfName() { return c().getString("menu-items.self.name"); }
    public List<String> selfLore() { return c().getStringList("menu-items.self.lore"); }

    public List<String> searchSignLines() { return c().getStringList("search.sign-lines"); }
    public long searchTimeoutSeconds() { return c().getLong("search.timeout-seconds", 60L); }

    public String reloadMessage() { return c().getString("messages.reload", "&#00FFAAĐã reload MarisLeaderboard."); }
    public String noPermissionMessage() { return c().getString("messages.no-permission", "&#FF5555Bạn không có quyền dùng lệnh này."); }
    public String playerOnlyMessage() { return c().getString("messages.player-only", "&#FF5555Chỉ người chơi mới dùng được lệnh này."); }

    public boolean soundEnabled(String key) { return soundsConfig.getBoolean("sounds." + key + ".enabled", true); }
    public Sound sound(String key) { return parseSound(soundsConfig.getString("sounds." + key + ".sound")); }
    public float soundVolume(String key, float fallback) { return (float) soundsConfig.getDouble("sounds." + key + ".volume", fallback); }
    public float soundPitch(String key, float fallback) { return (float) soundsConfig.getDouble("sounds." + key + ".pitch", fallback); }

    private Material matchMaterial(String input, Material fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        Material matched = Material.matchMaterial(input);
        return matched == null ? fallback : matched;
    }

    private Sound parseSound(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) {
            return null;
        }
        Sound sound = Registry.SOUNDS.get(key);
        if (sound != null) {
            return sound;
        }
        try {
            return Sound.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
