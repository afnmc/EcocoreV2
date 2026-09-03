package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * Periodically persists every in-memory mutable state EcoCore keeps
 * cached: player economy accounts and active minion data/storage,
 * at the interval configured in {@code config.yml general.autosave-interval-minutes}.
 */
public final class AutoSaveScheduler {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final EconomyEngine economyEngine;
    private final MinionManager minionManager;
    private final int intervalMinutes;

    private BukkitTask task;

    /**
     * Creates the autosave scheduler.
     *
     * @param plugin          the owning plugin instance
     * @param logger          plugin logger for save summaries
     * @param economyEngine   economy engine to autosave
     * @param minionManager   minion manager to autosave
     * @param intervalMinutes minutes between autosaves, from config.yml
     */
    public AutoSaveScheduler(JavaPlugin plugin, Logger logger, EconomyEngine economyEngine,
                              MinionManager minionManager, int intervalMinutes) {
        this.plugin = plugin;
        this.logger = logger;
        this.economyEngine = economyEngine;
        this.minionManager = minionManager;
        this.intervalMinutes = Math.max(1, intervalMinutes);
    }

    /**
     * Starts the repeating task, cancelling any previously running one first.
     */
    public void start() {
        stop();
        long periodTicks = intervalMinutes * 60L * 20L;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::runSave, periodTicks, periodTicks);
    }

    private void runSave() {
        economyEngine.saveAll();
        minionManager.saveAll();
        logger.info("[EcoCore] Autosave complete");
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