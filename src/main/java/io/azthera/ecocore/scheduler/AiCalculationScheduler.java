package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.ai.AiEconomyEngine;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs {@link AiEconomyEngine#runCycle()} on an async repeating task,
 * at the interval configured in {@code config.yml}.
 */
public final class AiCalculationScheduler {

    private final JavaPlugin plugin;
    private final AiEconomyEngine aiEconomyEngine;
    private final int intervalSeconds;

    private BukkitTask task;

    /**
     * Creates the AI calculation scheduler.
     *
     * @param plugin          the owning plugin instance
     * @param aiEconomyEngine the AI engine to run each cycle
     * @param intervalSeconds seconds between cycles, from config.yml
     */
    public AiCalculationScheduler(JavaPlugin plugin, AiEconomyEngine aiEconomyEngine, int intervalSeconds) {
        this.plugin = plugin;
        this.aiEconomyEngine = aiEconomyEngine;
        this.intervalSeconds = Math.max(20, intervalSeconds);
    }

    /**
     * Starts the repeating task, cancelling any previously running one first.
     */
    public void start() {
        stop();
        long periodTicks = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, aiEconomyEngine::runCycle, periodTicks, periodTicks);
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