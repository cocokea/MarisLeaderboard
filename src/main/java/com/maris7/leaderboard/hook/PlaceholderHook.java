package com.maris7.leaderboard.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderHook {
    private final JavaPlugin plugin;

    public PlaceholderHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String parse(OfflinePlayer player, String text) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return "0";
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
