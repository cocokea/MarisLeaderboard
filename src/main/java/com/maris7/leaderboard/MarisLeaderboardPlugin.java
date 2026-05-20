package com.maris7.leaderboard;

import com.github.retrooper.packetevents.PacketEvents;
import com.maris7.leaderboard.command.LeaderboardCommand;
import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.data.DatabaseManager;
import com.maris7.leaderboard.data.LeaderboardRepository;
import com.maris7.leaderboard.gui.LeaderboardGuiManager;
import com.maris7.leaderboard.hook.PlaceholderHook;
import com.maris7.leaderboard.listener.InventoryListener;
import com.maris7.leaderboard.listener.PlayerActivityListener;
import com.maris7.leaderboard.listener.SignInputPacketListener;
import com.maris7.leaderboard.service.LeaderboardService;
import com.maris7.leaderboard.service.SignSearchService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarisLeaderboardPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private DatabaseManager databaseManager;
    private LeaderboardRepository repository;
    private PlaceholderHook placeholderHook;
    private LeaderboardService leaderboardService;
    private LeaderboardGuiManager guiManager;
    private SignSearchService signSearchService;
    private SignInputPacketListener signInputPacketListener;

    @Override
    public void onEnable() {
        
        saveDefaultConfig();
        MarisPluginStartup.bootstrap(this, "cocokea/MarisLeaderboard");
saveDefaultConfig();
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        java.io.File soundsFile = getDataFolder().toPath().resolve("sounds.yml").toFile();
        if (!soundsFile.exists()) {
            saveResource("sounds.yml", false);
        }
        bootstrap(false);
    }

    public void reloadPlugin() {
        reloadConfig();
        bootstrap(true);
    }

    private void bootstrap(boolean reloading) {
        this.pluginConfig = new PluginConfig(this);

        if (!reloading) {
            this.databaseManager = new DatabaseManager(this, pluginConfig);
            try {
                databaseManager.start();
            } catch (NoClassDefFoundError error) {
                getLogger().severe("Khong tim thay thu vien Hikari/MySQL/SQLite luc khoi dong.");
                getLogger().severe("Hay dung dung file jar moi, xoa ban cu, khong load bang PlugMan, va restart server de Paper nap plugin.yml libraries.");
                throw error;
            }
            this.repository = new LeaderboardRepository(databaseManager, pluginConfig);
            repository.createTable();
            this.placeholderHook = new PlaceholderHook(this);
            this.leaderboardService = new LeaderboardService(this, pluginConfig, repository, placeholderHook);
            this.guiManager = new LeaderboardGuiManager(this, pluginConfig, leaderboardService);
            this.signSearchService = new SignSearchService(this, pluginConfig);
            if (signSearchService.isPacketEventsAvailable()) {
                this.signInputPacketListener = new SignInputPacketListener(signSearchService);
                PacketEvents.getAPI().getEventManager().registerListener(signInputPacketListener);
            } else {
                getLogger().warning("PacketEvents was not found. Sign input features will stay disabled until PacketEvents is installed.");
            }

            leaderboardService.start();

            PluginCommand command = getCommand("leaderboard");
            if (command != null) {
                LeaderboardCommand executor = new LeaderboardCommand(this, guiManager);
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }

            getServer().getPluginManager().registerEvents(signSearchService, this);
            getServer().getPluginManager().registerEvents(new InventoryListener(guiManager, leaderboardService, signSearchService, pluginConfig), this);
            getServer().getPluginManager().registerEvents(new PlayerActivityListener(pluginConfig, leaderboardService), this);
            return;
        }

        if (repository != null) {
            repository.createTable();
        }
        if (leaderboardService != null) {
            leaderboardService.refreshCache();
        }
        getLogger().info("MarisLeaderboard config reloaded.");
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public LeaderboardService getLeaderboardService() {
        return leaderboardService;
    }

    @Override
    public void onDisable() {
        if (signSearchService != null) signSearchService.clearAll();
        if (signInputPacketListener != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(signInputPacketListener);
            signInputPacketListener = null;
        }
        if (leaderboardService != null) leaderboardService.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
    }


}


