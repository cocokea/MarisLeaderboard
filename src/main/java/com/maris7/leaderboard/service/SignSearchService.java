package com.maris7.leaderboard.service;

import com.maris7.leaderboard.MarisLeaderboardPlugin;
import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.util.ColorUtil;
import com.maris7.leaderboard.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SignSearchService implements Listener {

    private static final String[] DEFAULT_PROMPT = {"^^^^^^^^^^^^", "&0[&2Search&0]", "&0Enter name", ""};

    private final MarisLeaderboardPlugin plugin;
    private final PluginConfig config;
    private final Map<UUID, LeaderboardSignInput> inputs = new ConcurrentHashMap<>();

    public SignSearchService(MarisLeaderboardPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open(Player player, Consumer<String> resultHandler) {
        UUID playerId = player.getUniqueId();
        close(playerId);

        SchedulerUtil.runPlayer(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }

            Location signLocation = chooseSignLocation(player);
            List<String> prompt = promptLines();

            SchedulerUtil.runAtLocation(plugin, signLocation, () -> createInput(playerId, signLocation, prompt, resultHandler));
        });
    }

    public boolean isWaiting(Player player) {
        return inputs.containsKey(player.getUniqueId());
    }

    public void clearAll() {
        for (UUID playerId : Set.copyOf(inputs.keySet())) {
            close(playerId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSignChange(SignChangeEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        LeaderboardSignInput input = inputs.get(playerId);
        if (input == null || !sameBlock(input.location(), event.getBlock().getLocation())) {
            return;
        }

        event.setCancelled(true);
        finish(event.getPlayer(), submittedQuery(event, input.promptLines()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        close(event.getPlayer().getUniqueId());
    }

    private void createInput(UUID playerId, Location location, List<String> prompt, Consumer<String> resultHandler) {
        if (!plugin.isEnabled()) {
            return;
        }

        Block block = location.getBlock();
        BlockState originalState = block.getState();

        block.setType(Material.OAK_SIGN, false);
        BlockState newState = block.getState();
        if (!(newState instanceof Sign sign)) {
            originalState.update(true, false);
            completeOnPlayerThread(playerId, resultHandler, "");
            return;
        }

        applyPrompt(sign, prompt);
        sign.setWaxed(false);
        sign.update(true, false);

        LeaderboardSignInput input = new LeaderboardSignInput(location, originalState, prompt, resultHandler);
        inputs.put(playerId, input);

        final String[] clientLines = prompt.toArray(String[]::new);
        SchedulerUtil.runPlayer(plugin, playerId, player -> {
            if (!player.isOnline()) {
                return;
            }
            player.sendSignChange(location, clientLines);
            SchedulerUtil.runPlayerLater(plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                World w = location.getWorld();
                if (w == null) {
                    return;
                }
                Block at = w.getBlockAt(location);
                if (!(at.getState() instanceof Sign openSign)) {
                    return;
                }
                player.openSign(openSign, Side.FRONT);
            }, 2L);
        });
        SchedulerUtil.runGlobalLater(plugin, () -> expire(playerId, input), Math.max(1L, config.searchTimeoutSeconds()) * 20L);
    }

    private void finish(Player player, String query) {
        UUID playerId = player.getUniqueId();
        LeaderboardSignInput input = inputs.remove(playerId);
        if (input == null) {
            return;
        }

        restore(input);
        SchedulerUtil.runPlayer(plugin, player, () -> input.resultHandler().accept(query));
    }

    private void expire(UUID playerId, LeaderboardSignInput expectedInput) {
        LeaderboardSignInput current = inputs.get(playerId);
        if (current != expectedInput) {
            return;
        }
        close(playerId);
    }

    private void close(UUID playerId) {
        LeaderboardSignInput input = inputs.remove(playerId);
        if (input == null) {
            return;
        }

        restore(input);
    }

    private void completeOnPlayerThread(UUID playerId, Consumer<String> resultHandler, String value) {
        SchedulerUtil.runPlayerOrElse(plugin, playerId, player -> resultHandler.accept(value), () -> { });
    }

    private void restore(LeaderboardSignInput input) {
        Runnable restoreTask = () -> {
            try {
                input.originalState().update(true, false);
            } catch (Throwable ignored) {
            }
        };

        if (!plugin.isEnabled()) {
            restoreTask.run();
            return;
        }
        SchedulerUtil.runAtLocation(plugin, input.location(), restoreTask);
    }

    private Location chooseSignLocation(Player player) {
        Location base = player.getLocation().getBlock().getLocation();
        World world = base.getWorld();
        if (world == null) {
            return base;
        }

        int y = Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight(), base.getBlockY() + 2));
        return new Location(world, base.getBlockX(), y, base.getBlockZ());
    }

    private List<String> promptLines() {
        List<String> configured = config.searchSignLines();
        List<String> prompt = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            String value = index < configured.size() ? configured.get(index) : DEFAULT_PROMPT[index];
            prompt.add(ColorUtil.color(value == null ? "" : value));
        }
        return prompt;
    }

    private void applyPrompt(Sign sign, List<String> prompt) {
        for (int index = 0; index < 4; index++) {
            Component line = ColorUtil.component(prompt.get(index));
            sign.getSide(Side.FRONT).line(index, line);
            sign.getSide(Side.BACK).line(index, Component.empty());
        }
    }

    private String submittedQuery(SignChangeEvent event, List<String> prompt) {
        Set<String> promptLines = normalizedPromptLines(prompt);
        PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();

        for (int index = 0; index < 4; index++) {
            String line = serializer.serialize(event.line(index)).trim();
            if (line.isBlank()) {
                continue;
            }
            if (!promptLines.contains(normalize(line))) {
                return line;
            }
        }
        return "";
    }

    private Set<String> normalizedPromptLines(List<String> prompt) {
        Set<String> lines = new HashSet<>();
        for (String line : prompt) {
            String normalized = normalize(line);
            if (!normalized.isBlank()) {
                lines.add(normalized);
            }
        }
        return lines;
    }

    private String normalize(String value) {
        return ColorUtil.stripColor(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private boolean sameBlock(Location first, Location second) {
        return first != null
                && second != null
                && first.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private record LeaderboardSignInput(
            Location location,
            BlockState originalState,
            List<String> promptLines,
            Consumer<String> resultHandler
    ) {
    }
}
