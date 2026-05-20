package com.maris7.leaderboard.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import com.maris7.leaderboard.MarisLeaderboardPlugin;
import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.util.ColorUtil;
import com.maris7.leaderboard.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    private static final int SIGN_LINE_COUNT = 4;
    private static final WrappedBlockState FAKE_SIGN_STATE = WrappedBlockState.getByString("minecraft:oak_sign[rotation=0,waterlogged=false]");
    private static final String[] DEFAULT_PROMPT = {"^^^^^^^^^^^^", "&0[&2Search&0]", "&0Enter name", ""};

    private final MarisLeaderboardPlugin plugin;
    private final PluginConfig config;
    private final Map<UUID, LeaderboardSignInput> inputs = new ConcurrentHashMap<>();

    public SignSearchService(MarisLeaderboardPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isPacketEventsAvailable() {
        return PacketEvents.getAPI() != null;
    }

    public void open(Player player, Consumer<String> resultHandler) {
        UUID playerId = player.getUniqueId();
        close(playerId);
        if (!isPacketEventsAvailable()) {
            plugin.getLogger().warning("PacketEvents is not available; cannot open sign input for " + player.getName());
            return;
        }

        SchedulerUtil.runPlayer(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location signLocation = chooseSignLocation(player);
            List<String> prompt = promptLines();
            Block block = signLocation.getBlock();
            LeaderboardSignInput input = new LeaderboardSignInput(
                    signLocation.clone(),
                    block.getBlockData().clone(),
                    prompt,
                    normalizedPromptLines(prompt),
                    resultHandler
            );
            inputs.put(playerId, input);
            sendFakeSign(player, input);
            SchedulerUtil.runGlobalLater(plugin, () -> expire(playerId, input), Math.max(1L, config.searchTimeoutSeconds()) * 20L);
        });
    }

    public boolean hasSession(UUID playerId) {
        return inputs.containsKey(playerId);
    }

    public void handleSignResponse(Player player, Vector3i position, String[] lines) {
        if (player == null || position == null) {
            return;
        }
        SchedulerUtil.runPlayer(plugin, player, () -> {
            LeaderboardSignInput input = inputs.get(player.getUniqueId());
            if (input == null || !input.matches(position)) {
                return;
            }
            finish(player, submittedQuery(lines, input.normalizedPromptLines()));
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        close(event.getPlayer().getUniqueId());
    }

    private void sendFakeSign(Player player, LeaderboardSignInput input) {
        Vector3i position = input.position();
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerBlockChange(position, FAKE_SIGN_STATE));
        player.sendSignChange(input.location(), colorLines(input.promptLines()));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerOpenSignEditor(position, true));
    }

    private void finish(Player player, String query) {
        UUID playerId = player.getUniqueId();
        LeaderboardSignInput input = inputs.remove(playerId);
        if (input == null) {
            return;
        }

        restore(player, input);
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
        SchedulerUtil.runPlayer(plugin, playerId, player -> restore(player, input));
    }

    private void restore(Player player, LeaderboardSignInput input) {
        if (!player.isOnline()) {
            return;
        }
        player.sendBlockChange(input.location(), input.originalData());
    }

    private Location chooseSignLocation(Player player) {
        Location base = player.getLocation();
        int y = Math.max(player.getWorld().getMinHeight() + 1, base.getBlockY() + 5);
        return new Location(player.getWorld(), base.getBlockX(), y, base.getBlockZ());
    }

    private List<String> promptLines() {
        List<String> configured = config.searchSignLines();
        List<String> prompt = new ArrayList<>(SIGN_LINE_COUNT);
        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            String value = index < configured.size() ? configured.get(index) : DEFAULT_PROMPT[index];
            prompt.add(value == null ? "" : value);
        }
        return prompt;
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

    private String submittedQuery(String[] lines, Set<String> prompt) {
        if (lines == null) {
            return "";
        }
        for (String line : lines) {
            String normalized = normalize(line);
            if (!normalized.isBlank() && !prompt.contains(normalized)) {
                return line == null ? "" : line.trim();
            }
        }
        return "";
    }

    private String normalize(String value) {
        return ColorUtil.stripColor(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private String[] colorLines(List<String> lines) {
        String[] colored = new String[SIGN_LINE_COUNT];
        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            String line = index < lines.size() ? lines.get(index) : "";
            colored[index] = ColorUtil.color(line);
        }
        return colored;
    }

    private record LeaderboardSignInput(
            Location location,
            org.bukkit.block.data.BlockData originalData,
            List<String> promptLines,
            Set<String> normalizedPromptLines,
            Consumer<String> resultHandler
    ) {
        private Vector3i position() {
            return new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private boolean matches(Vector3i other) {
            return other.getX() == location.getBlockX()
                    && other.getY() == location.getBlockY()
                    && other.getZ() == location.getBlockZ();
        }
    }
}
