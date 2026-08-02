package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.inflation.InflationEngine;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs {@link InflationEngine#runCycle()} on an async repeating task,
 * at the interval configured in {@code config.yml}.
 */
public final class InflationTaskScheduler {

    private final JavaPlugin plugin;
    private final InflationEngine inflationEngine;
    private final int intervalSeconds;

    private BukkitTask task;

    /**
     * Creates the inflation task scheduler.
     *
     * @param plugin          the owning plugin instance
     * @param inflationEngine the inflation engine to run each cycle
     * @param intervalSeconds seconds between cycles, from config.yml
     */
    public InflationTaskScheduler(JavaPlugin plugin, InflationEngine inflationEngine, int intervalSeconds) {
        this.plugin = plugin;
        this.inflationEngine = inflationEngine;
        this.intervalSeconds = Math.max(20, intervalSeconds);
    }

    /**
     * Starts the repeating task, cancelling any previously running one first.
     */
    public void start() {
        stop();
        long periodTicks = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, inflationEngine::runCycle, periodTicks, periodTicks);
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