package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs {@link MinionManager#tickAll(long)} on a synchronous repeating
 * task (minion AI touches Bukkit world/entity APIs, which must run
 * on the main thread), at the interval configured in {@code config.yml}.
 */
public final class MinionTickScheduler {

    private final JavaPlugin plugin;
    private final MinionManager minionManager;
    private final int intervalSeconds;

    private BukkitTask task;

    public MinionTickScheduler(JavaPlugin plugin, MinionManager minionManager, int intervalSeconds) {
        this.plugin = plugin;
        this.minionManager = minionManager;
        this.intervalSeconds = Math.max(1, intervalSeconds);
    }

    public void start() {
        stop();
        long periodTicks = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, () -> minionManager.tickAll(periodTicks), periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}