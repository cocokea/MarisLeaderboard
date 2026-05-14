package com.maris7.leaderboard.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SchedulerUtil {

    private static final boolean FOLIA = detectFolia();

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void later3Ticks(Plugin plugin, Runnable runnable) {
        runGlobalLater(plugin, runnable, 3L);
    }

    public static void sync(Plugin plugin, Runnable runnable) {
        runGlobal(plugin, runnable);
    }

    public static void runGlobal(Plugin plugin, Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runnable, "runnable");
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
            invokeConsumerMethod(scheduler, "run", plugin, runnable);
        } catch (Throwable throwable) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runnable, "runnable");
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
            return;
        }
        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
            invokeConsumerMethod(scheduler, "runDelayed", plugin, runnable, delayTicks);
        } catch (Throwable throwable) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(runnable, "runnable");
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getRegionScheduler").invoke(plugin.getServer());
            invokeLocationConsumerMethod(scheduler, plugin, location, runnable);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to schedule location task on Folia for " + formatLocation(location), throwable);
        }
    }

    public static void runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        Objects.requireNonNull(entity, "entity");
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            invokeEntityConsumerMethod(scheduler, "run", plugin, runnable, new Object[0]);
        } catch (Throwable throwable) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runEntityLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
            return;
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            invokeEntityConsumerMethod(scheduler, "runDelayed", plugin, runnable, delayTicks);
        } catch (Throwable throwable) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static void runPlayer(Plugin plugin, UUID playerId, Consumer<Player> consumer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(consumer, "consumer");
        if (!FOLIA) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            runEntity(plugin, player, () -> {
                if (player.isOnline()) {
                    consumer.accept(player);
                }
            });
            return;
        }
        runGlobal(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            runEntity(plugin, player, () -> {
                if (player.isOnline()) {
                    consumer.accept(player);
                }
            });
        });
    }

    public static void runPlayerOrElse(Plugin plugin, UUID playerId, Consumer<Player> consumer, Runnable offlineAction) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(offlineAction, "offlineAction");
        runGlobal(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                offlineAction.run();
                return;
            }
            runEntity(plugin, player, () -> {
                if (player.isOnline()) {
                    consumer.accept(player);
                } else {
                    offlineAction.run();
                }
            });
        });
    }

    public static void onlinePlayerSnapshot(Plugin plugin, Consumer<List<UUID>> consumer) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(consumer, "consumer");
        runGlobal(plugin, () -> {
            List<UUID> players = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            consumer.accept(players);
        });
    }

    public static void runPlayer(Plugin plugin, Player player, Runnable runnable) {
        if (player == null) return;
        runEntity(plugin, player, () -> {
            if (player.isOnline()) {
                runnable.run();
            }
        });
    }

    public static void runPlayerLater(Plugin plugin, Player player, Runnable runnable, long delayTicks) {
        if (player == null) return;
        runEntityLater(plugin, player, () -> {
            if (player.isOnline()) {
                runnable.run();
            }
        }, delayTicks);
    }

    public static TaskHandle runPlayerTimer(Plugin plugin, Player player, Runnable runnable, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(runnable, "runnable");
        if (!FOLIA) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) return;
                runnable.run();
            }, delayTicks, periodTicks);
            return new TaskHandle(task);
        }
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        final long safePeriodTicks = Math.max(1L, periodTicks);
        final class FoliaRepeatingTask {
            void schedule(long nextDelayTicks) {
                if (cancelled.get()) {
                    return;
                }
                Runnable execution = () -> {
                    if (cancelled.get() || !player.isOnline()) {
                        return;
                    }
                    runnable.run();
                    schedule(safePeriodTicks);
                };
                if (nextDelayTicks <= 0L) {
                    runEntity(plugin, player, execution);
                } else {
                    runEntityLater(plugin, player, execution, nextDelayTicks);
                }
            }
        }
        new FoliaRepeatingTask().schedule(delayTicks);
        return new TaskHandle(() -> cancelled.set(true));
    }

    public static TaskHandle runAsyncTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runnable, "runnable");
        if (!FOLIA) {
            return new TaskHandle(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks));
        }
        try {
            Object scheduler = plugin.getServer().getClass().getMethod("getAsyncScheduler").invoke(plugin.getServer());
            Method method = findMethod(scheduler.getClass(), "runAtFixedRate", 5);
            Object task = method.invoke(scheduler, plugin, wrapConsumer(runnable), ticksToMillis(delayTicks), ticksToMillis(periodTicks), TimeUnit.MILLISECONDS);
            return new TaskHandle(task);
        } catch (Throwable throwable) {
            return new TaskHandle(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks));
        }
    }

    private static Object invokeConsumerMethod(Object scheduler, String methodName, Plugin plugin, Runnable runnable, Object... extraArgs) throws Exception {
        Method method = findMethod(scheduler.getClass(), methodName, 2 + extraArgs.length);
        Object[] args = new Object[2 + extraArgs.length];
        args[0] = plugin;
        args[1] = wrapConsumer(runnable);
        System.arraycopy(extraArgs, 0, args, 2, extraArgs.length);
        return method.invoke(scheduler, args);
    }

    private static void invokeLocationConsumerMethod(Object scheduler, Plugin plugin, Location location, Runnable runnable) throws Exception {
        try {
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
            method.setAccessible(true);
            method.invoke(scheduler, plugin, location, wrapConsumer(runnable));
            return;
        } catch (NoSuchMethodException ignored) {
        }

        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        Method method = findLocationMethod(scheduler.getClass(), "run");
        method.invoke(scheduler, plugin, world, chunkX, chunkZ, wrapConsumer(runnable));
    }

    private static Object invokeEntityConsumerMethod(Object scheduler, String methodName, Plugin plugin, Runnable runnable, Object... extraArgs) throws Exception {
        Method method = findMethod(scheduler.getClass(), methodName, 3 + extraArgs.length);
        Object[] args = new Object[3 + extraArgs.length];
        args[0] = plugin;
        args[1] = wrapConsumer(runnable);
        args[2] = (Runnable) () -> {};
        System.arraycopy(extraArgs, 0, args, 3, extraArgs.length);
        return method.invoke(scheduler, args);
    }

    private static Consumer<Object> wrapConsumer(Runnable runnable) {
        return ignored -> runnable.run();
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + '#' + name + '/' + parameterCount);
    }

    private static Method findLocationMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (!method.getName().equals(name) || params.length != 5) {
                continue;
            }
            if (Plugin.class.isAssignableFrom(params[0])
                    && World.class.isAssignableFrom(params[1])
                    && isIntLike(params[2])
                    && isIntLike(params[3])
                    && Consumer.class.isAssignableFrom(params[4])) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + '#' + name + "/location");
    }

    private static boolean isIntLike(Class<?> type) {
        return type == Integer.TYPE || type == Integer.class;
    }

    private static String formatLocation(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "null" : world.getName();
        return worldName + ':' + location.getBlockX() + ',' + location.getBlockY() + ',' + location.getBlockZ();
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(1L, ticks * 50L);
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static final class TaskHandle {
        private final Object delegate;
        private final Runnable cancelAction;

        public TaskHandle(Object delegate) {
            this(delegate, null);
        }

        public TaskHandle(Runnable cancelAction) {
            this(null, cancelAction);
        }

        public TaskHandle(Object delegate, Runnable cancelAction) {
            this.delegate = delegate;
            this.cancelAction = cancelAction;
        }

        public void cancel() {
            if (cancelAction != null) {
                cancelAction.run();
            }
            if (delegate == null) return;
            try {
                Method method = delegate.getClass().getMethod("cancel");
                method.invoke(delegate);
            } catch (Throwable ignored) {
            }
        }
    }
}
