package com.maris7.leaderboard.listener;

import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.service.LeaderboardService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerActivityListener implements Listener {
    private final PluginConfig config;
    private final LeaderboardService service;

    public PlayerActivityListener(PluginConfig config, LeaderboardService service) {
        this.config = config;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (config.updateOnJoin()) service.queueUpdate(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (config.updateOnQuit()) service.queueUpdate(event.getPlayer());
    }
}
