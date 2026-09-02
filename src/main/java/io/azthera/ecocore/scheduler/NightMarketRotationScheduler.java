package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.market.NightMarketManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Periodically checks whether the night market's rotation has expired
 * and triggers {@link NightMarketManager#rotate()} if so. Checks
 * every 5 minutes rather than scheduling a single fixed-period task
 * at the configured interval, so a late server start or reload
 * doesn't keep pushing rotations further and further out.
 */
public final class NightMarketRotationScheduler {

    private final JavaPlugin plugin;
    private final NightMarketManager nightMarketManager;

    private BukkitTask task;

    /**
     * Creates the rotation scheduler.
     *
     * @param plugin             the owning plugin instance
     * @param nightMarketManager the night market manager to rotate
     */
    public NightMarketRotationScheduler(JavaPlugin plugin, NightMarketManager nightMarketManager) {
        this.plugin = plugin;
        this.nightMarketManager = nightMarketManager;
    }

    /**
     * Starts the repeating check task, cancelling any previously running one first.
     */
    public void start() {
        stop();
        long checkPeriodTicks = 20L * 60 * 5;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                long remaining = nightMarketManager.millisUntilNextRotation();
                if (remaining <= 0) {
                    plugin.getLogger().info("[EcoCore] Night market rotation due, rotating now...");
                    nightMarketManager.rotate();
                }
            } catch (Exception exception) {
                // Previously, any exception thrown here (e.g. a transient
                // SQLITE_BUSY lock during rotate()'s DB writes) would just be
                // logged by Bukkit's own scheduler wrapper, and the market
                // would silently never rotate again if the same failure kept
                // recurring. Logging it explicitly here makes a stuck
                // rotation loud in console instead of quietly frozen forever.
                plugin.getLogger().log(Level.SEVERE,
                        "[EcoCore] Night market rotation check failed, will retry in 5 minutes", exception);
            }
        }, checkPeriodTicks, checkPeriodTicks);

        plugin.getLogger().info("[EcoCore] Night market rotation scheduler started (checking every 5 minutes). "
                + "If you never see this line in console, 'night-market-enabled' is off in config.yml.");
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