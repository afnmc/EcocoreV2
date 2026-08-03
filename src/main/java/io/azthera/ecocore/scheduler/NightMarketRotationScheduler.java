package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.market.NightMarketManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
            if (nightMarketManager.millisUntilNextRotation() <= 0) {
                nightMarketManager.rotate();
            }
        }, checkPeriodTicks, checkPeriodTicks);
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
