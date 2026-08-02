package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs {@link MinionManager#tickAll()} on a synchronous repeating
 * task (minion AI touches Bukkit world/entity APIs, which must run
 * on the main thread), at the interval configured in {@code config.yml}.
 */
public final class MinionTickScheduler {

    private final JavaPlugin plugin;
    private final MinionManager minionManager;
    private final int intervalSeconds;

    private BukkitTask task;

    /**
     * Creates the minion tick scheduler.
     *
     * @param plugin          the owning plugin instance
     * @param minionManager   the minion manager to tick each interval
     * @param intervalSeconds seconds between ticks, from config.yml
     */
    public MinionTickScheduler(JavaPlugin plugin, MinionManager minionManager, int intervalSeconds) {
        this.plugin = plugin;
        this.minionManager = minionManager;
        this.intervalSeconds = Math.max(1, intervalSeconds);
    }

    /**
     * Starts the repeating task, cancelling any previously running one first.
     */
    public void start() {
        stop();
        long periodTicks = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, minionManager::tickAll, periodTicks, periodTicks);
    }

    /**
     * Stops the repeating task if currently running.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}