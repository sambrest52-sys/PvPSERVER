package net.pvpserver.core.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small scheduler facade so the rest of the code base never touches {@link org.bukkit.scheduler.BukkitScheduler} directly.
 */
public final class Tasks {

    private static Plugin plugin;

    private Tasks() {
    }

    /**
     * Binds the helper to the owning plugin. Called once by PvPCore on enable.
     *
     * @param owner plugin used for scheduling
     */
    public static void init(Plugin owner) {
        plugin = owner;
    }

    /**
     * Runs a task on the main thread; runs inline if already on the main thread.
     *
     * @param runnable work to run
     */
    public static void sync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Runs a task on the main thread after a delay.
     *
     * @param runnable work to run
     * @param delayTicks delay in ticks
     * @return the scheduled task
     */
    public static BukkitTask later(Runnable runnable, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    /**
     * Runs a repeating main-thread task.
     *
     * @param runnable work to run
     * @param delayTicks initial delay
     * @param periodTicks period
     * @return the scheduled task
     */
    public static BukkitTask timer(Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    /**
     * Runs a repeating asynchronous task.
     *
     * @param runnable work to run
     * @param delayTicks initial delay
     * @param periodTicks period
     * @return the scheduled task
     */
    public static BukkitTask asyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
    }

    /**
     * Runs a task asynchronously on the Bukkit async pool.
     *
     * @param runnable work to run
     */
    public static void async(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    /**
     * Consumes the result of a future back on the main thread. Exceptions are logged, never swallowed silently.
     *
     * @param future future to observe
     * @param consumer main-thread consumer
     * @param <T> result type
     */
    public static <T> void thenSync(CompletableFuture<T> future, Consumer<T> consumer) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Async task failed", error);
                return;
            }
            sync(() -> consumer.accept(value));
        });
    }

    /**
     * Supplies a value on the main thread and returns it as a future (usable from async code).
     *
     * @param supplier main-thread supplier
     * @param <T> type
     * @return future completed on the main thread
     */
    public static <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        sync(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * @return the plugin that owns the scheduler facade
     */
    public static Plugin plugin() {
        return plugin;
    }
}
