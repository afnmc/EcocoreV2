package io.azthera.ecocore.manager;

import io.azthera.ecocore.database.dao.JobsDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.jobs.JobMissionManager;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobMissionRecord;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.shop.ShopFavoriteManager;
import io.azthera.ecocore.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Coordinates the per-player join/quit lifecycle across every module
 * that keeps in-memory session state: loads/unloads the economy
 * account, clears session-only shop-favorite state, and refreshes
 * daily/weekly job missions when a player's missions have gone stale.
 */
public final class PlayerDataManager {

    private final Logger logger;
    private final JavaPlugin plugin;
    private final EconomyEngine economyEngine;
    private final ShopFavoriteManager shopFavoriteManager;
    private final JobMissionManager jobMissionManager;
    private final JobsDao jobsDao;
    private final JobsManager jobsManager;

    public PlayerDataManager(Logger logger, JavaPlugin plugin, EconomyEngine economyEngine,
                               ShopFavoriteManager shopFavoriteManager, JobMissionManager jobMissionManager,
                               JobsDao jobsDao, JobsManager jobsManager) {
        this.logger = logger;
        this.plugin = plugin;
        this.economyEngine = economyEngine;
        this.shopFavoriteManager = shopFavoriteManager;
        this.jobMissionManager = jobMissionManager;
        this.jobsDao = jobsDao;
        this.jobsManager = jobsManager;
    }

    public PlayerDataManager(Logger logger, JavaPlugin plugin, EconomyEngine economyEngine,
                               ShopFavoriteManager shopFavoriteManager, JobMissionManager jobMissionManager,
                               JobsDao jobsDao) {
        this(logger, plugin, economyEngine, shopFavoriteManager, jobMissionManager, jobsDao, null);
    }

    public PlayerDataManager(Logger logger, EconomyEngine economyEngine,
                               ShopFavoriteManager shopFavoriteManager, JobMissionManager jobMissionManager) {
        this(logger, null, economyEngine, shopFavoriteManager, jobMissionManager, null, null);
    }

    /**
     * Handles async pre-login: loads the economy account into cache
     * off the main thread so it is ready by the time join events fire.
     *
     * @param uuid player's uuid
     * @param name player's name
     */
    public void handlePreLogin(UUID uuid, String name) {
        economyEngine.loadAccount(uuid, name);
    }

    /**
     * Handles a player join: the economy account was already preloaded
     * during AsyncPlayerPreLoginEvent, so this only refreshes stale
     * daily/weekly job missions asynchronously.
     *
     * @param player the joining player
     */
    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        var pluginRef = this.plugin != null ? this.plugin : Bukkit.getPluginManager().getPlugin("EcoCore");
        if (pluginRef != null && pluginRef.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(pluginRef, () -> {
                refreshStaleMissions(uuid);
            });
        } else {
            refreshStaleMissions(uuid);
        }
    }

    private void refreshStaleMissions(UUID playerUuid) {
        try {
            Set<JobType> relevantJobs;
            if (jobsDao != null) {
                List<JobData> joined = jobsDao.findAllForPlayer(playerUuid);
                relevantJobs = joined.stream().map(JobData::getJobType).collect(Collectors.toSet());
            } else {
                relevantJobs = EnumSet.allOf(JobType.class);
            }

            if (relevantJobs.isEmpty()) {
                return;
            }

            List<JobMissionRecord> allActive = jobMissionManager.getActiveMissions(playerUuid);

            for (JobType type : relevantJobs) {
                List<JobMissionRecord> dailyMissions = allActive.stream()
                        .filter(m -> m.jobType() == type && m.period().equals(JobMissionManager.PERIOD_DAILY))
                        .toList();

                boolean dailyStale = dailyMissions.isEmpty()
                        || dailyMissions.stream().allMatch(m -> TimeUtils.isBeforeToday(m.assignedAt()));

                if (dailyStale) {
                    jobMissionManager.assignDailyMissions(playerUuid, type);
                }

                List<JobMissionRecord> weeklyMissions = allActive.stream()
                        .filter(m -> m.jobType() == type && m.period().equals(JobMissionManager.PERIOD_WEEKLY))
                        .toList();

                boolean weeklyStale = weeklyMissions.isEmpty()
                        || weeklyMissions.stream().allMatch(m -> TimeUtils.isBeforeThisWeek(m.assignedAt()));

                if (weeklyStale) {
                    jobMissionManager.assignWeeklyMissions(playerUuid, type);
                }
            }
        } catch (SQLException exception) {
            logger.warning("[EcoCore] Failed to refresh missions for "
                    + playerUuid + ": " + exception.getMessage());
        }
    }

    /**
     * Handles a player quit: saves and unloads their economy account,
     * cleans up per-player locks/queues, and clears session-only state
     * asynchronously without blocking the Bukkit main thread.
     *
     * @param player the quitting player
     */
    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        shopFavoriteManager.clear(uuid);
        var pluginRef = this.plugin != null ? this.plugin : Bukkit.getPluginManager().getPlugin("EcoCore");
        if (pluginRef != null && pluginRef.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(pluginRef, () -> {
                economyEngine.unloadAccount(uuid);
                if (jobsManager != null) {
                    jobsManager.cleanupPlayer(uuid);
                }
            });
        } else {
            economyEngine.unloadAccount(uuid);
            if (jobsManager != null) {
                jobsManager.cleanupPlayer(uuid);
            }
        }
    }
}
