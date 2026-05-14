package com.maris7.leaderboard.command;

import com.maris7.leaderboard.MarisLeaderboardPlugin;
import com.maris7.leaderboard.gui.LeaderboardGuiManager;
import com.maris7.leaderboard.util.ColorUtil;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final MarisLeaderboardPlugin plugin;
    private final LeaderboardGuiManager guiManager;

    public LeaderboardCommand(MarisLeaderboardPlugin plugin, LeaderboardGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("marisleaderboard.reload")) {
                sender.sendMessage(ColorUtil.color(plugin.getPluginConfig().noPermissionMessage()));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(ColorUtil.color(plugin.getPluginConfig().reloadMessage()));
            if (sender instanceof Player player) {
                guiManager.playSound(player, "reload", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.25f);
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color(plugin.getPluginConfig().playerOnlyMessage()));
            return true;
        }
        if (plugin.getPluginConfig().updateOnCommandOpen()) {
            plugin.getLeaderboardService().queueUpdate(player);
        }
        guiManager.openMainMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
