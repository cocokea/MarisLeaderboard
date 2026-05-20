package com.maris7.leaderboard.service;

import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.data.LeaderboardRepository;
import com.maris7.leaderboard.hook.PlaceholderHook;
import com.maris7.leaderboard.model.LeaderboardCategory;
import com.maris7.leaderboard.model.LeaderboardEntry;
import com.maris7.leaderboard.model.PlayerLeaderboardData;
import com.maris7.leaderboard.util.NumberFormatUtil;
import com.maris7.leaderboard.util.SchedulerUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LeaderboardService {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final LeaderboardRepository repository;
    private final PlaceholderHook placeholderHook;
    private final ExecutorService executorService;
    private final Map<String, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public LeaderboardService(JavaPlugin plugin, PluginConfig config, LeaderboardRepository repository, PlaceholderHook placeholderHook) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.placeholderHook = placeholderHook;
        this.executorService = Executors.newFixedThreadPool(Math.max(1, config.asyncThreads()));
    }

    public void start() {
        SchedulerUtil.runAsyncTimer(plugin, this::refreshAllOnline, 20L, config.refreshTicks());
        SchedulerUtil.runAsyncTimer(plugin, () -> executorService.submit(this::refreshCache), 40L, config.refreshTicks());
    }

    public void shutdown() {
        shuttingDown.set(true);
        executorService.shutdownNow();
    }

    public void queueUpdate(Player player) {
        if (shuttingDown.get()) {
            return;
        }
        UUID uniqueId = player.getUniqueId();
        if (!queuedPlayers.add(uniqueId)) {
            return;
        }
        readPlayer(uniqueId).thenAccept(data -> {
            try {
                if (data != null && !shuttingDown.get() && !executorService.isShutdown()) {
                    executorService.submit(() -> repository.upsert(data, config.categories()));
                }
            } finally {
                queuedPlayers.remove(uniqueId);
            }
        });
    }

    public void refreshAllOnline() {
        if (shuttingDown.get()) {
            return;
        }
        SchedulerUtil.onlinePlayerSnapshot(plugin, playerIds -> {
            if (shuttingDown.get()) {
                return;
            }
            if (playerIds.isEmpty()) {
                return;
            }
            List<CompletableFuture<PlayerLeaderboardData>> futures = playerIds.stream()
                    .map(this::readPlayer)
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
                List<PlayerLeaderboardData> batch = futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList();
                if (!batch.isEmpty() && !shuttingDown.get() && !executorService.isShutdown()) {
                    executorService.submit(() -> repository.upsertBatch(batch, config.categories()));
                }
            });
        });
    }

    private CompletableFuture<PlayerLeaderboardData> readPlayer(UUID playerId) {
        if (shuttingDown.get()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PlayerLeaderboardData> future = new CompletableFuture<>();
        SchedulerUtil.runPlayerOrElse(plugin, playerId, player -> {
            try {
                if (shuttingDown.get()) {
                    future.complete(null);
                    return;
                }
                future.complete(readPlayerOnPlayerThread(player));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }, () -> future.complete(null));
        return future.exceptionally(throwable -> null);
    }

    private PlayerLeaderboardData readPlayerOnPlayerThread(Player player) {
        PlayerLeaderboardData data = new PlayerLeaderboardData(player.getUniqueId(), player.getName());
        for (LeaderboardCategory category : config.categories()) {
            String raw = placeholderHook.parse(player, config.categoryPlaceholder(category));
            double numeric = NumberFormatUtil.parseNumber(raw);
            data.rawValues().put(category.key(), raw);
            data.numericValues().put(category.key(), numeric);
        }
        data.updatedAt(System.currentTimeMillis());
        return data;
    }

    public void refreshCache() {
        if (shuttingDown.get()) {
            return;
        }
        for (LeaderboardCategory category : config.categories()) {
            cache.put(category.key(), repository.top(category, config.cacheTopSize()));
        }
    }

    public List<LeaderboardEntry> top(LeaderboardCategory category) {
        return cache.computeIfAbsent(category.key(), c -> repository.top(category, config.cacheTopSize()));
    }

    public CompletableFuture<List<LeaderboardEntry>> search(LeaderboardCategory category, String query) {
        if (shuttingDown.get() || executorService.isShutdown()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> repository.search(category, query, config.maxSearchResults()), executorService);
    }

    public CompletableFuture<Optional<LeaderboardEntry>> self(LeaderboardCategory category, UUID uuid) {
        if (shuttingDown.get() || executorService.isShutdown()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> repository.self(category, uuid), executorService);
    }

    public void forceRefresh(Player player) {
        if (shuttingDown.get()) {
            return;
        }
        queueUpdate(player);
        if (!executorService.isShutdown()) {
            executorService.submit(this::refreshCache);
        }
    }

    public String displayValue(LeaderboardCategory category, LeaderboardEntry entry) {
        if (category.compact()) return NumberFormatUtil.compact(entry.numericValue());
        return entry.displayValue();
    }
}
