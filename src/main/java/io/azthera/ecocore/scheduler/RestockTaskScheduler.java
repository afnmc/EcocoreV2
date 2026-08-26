package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.manager.NotificationManager;
import io.azthera.ecocore.shop.RestockScheduler;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Runs {@link RestockScheduler#runRestockPass} on an async repeating
 * task, at the interval configured in {@code config.yml}, determining
 * once per pass whether this tick also coincides with the daily/
 * weekly restock windows (the first pass after UTC midnight / the
 * first pass after a new ISO week begins).
 */
public final class RestockTaskScheduler {

    private final JavaPlugin plugin;
    private final RestockScheduler restockScheduler;
    private final ShopManager shopManager;
    private final NotificationManager notificationManager;
    private final int intervalSeconds;

    private BukkitTask task;
    private long lastDailyBoundary = -1;
    private long lastWeeklyBoundary = -1;

    /**
     * Creates the restock task scheduler.
     *
     * @param plugin              the owning plugin instance
     * @param restockScheduler    the restock scheduler to run each pass
     * @param shopManager         shared shop manager, used to resolve items for notifications
     * @param notificationManager notification manager, used to announce restocks
     * @param intervalSeconds     seconds between passes, from config.yml
     */
    public RestockTaskScheduler(JavaPlugin plugin, RestockScheduler restockScheduler, ShopManager shopManager,
                                 NotificationManager notificationManager, int intervalSeconds) {
        this.plugin = plugin;
        this.restockScheduler = restockScheduler;
        this.shopManager = shopManager;
        this.notificationManager = notificationManager;
        this.intervalSeconds = Math.max(5, intervalSeconds);
    }

    /**
     * Starts the repeating task, cancelling any previously running one first.
     */
    public void start() {
        stop();
        long periodTicks = intervalSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::runPass, periodTicks, periodTicks);
    }

    private void runPass() {
        long currentDailyBoundary = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        long currentWeeklyBoundary = (Instant.now().getEpochSecond() / (7 * 86400)) * (7 * 86400);

        boolean isDailyTick = currentDailyBoundary != lastDailyBoundary;
        boolean isWeeklyTick = currentWeeklyBoundary != lastWeeklyBoundary;

        lastDailyBoundary = currentDailyBoundary;
        lastWeeklyBoundary = currentWeeklyBoundary;

        var outcomes = restockScheduler.runRestockPass(isDailyTick, isWeeklyTick);
        notificationManager.announceRestockBatch(outcomes, shopManager::getItem);
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