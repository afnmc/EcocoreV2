package io.azthera.ecocore.manager;

import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.jobs.JobMissionManager;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.shop.ShopFavoriteManager;
import io.azthera.ecocore.utils.TimeUtils;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Coordinates the per-player join/quit lifecycle across every module
 * that keeps in-memory session state: loads/unloads the economy
 * account, clears session-only shop-favorite state, and refreshes
 * daily/weekly job missions when a player's missions have gone stale.
 */
public final class PlayerDataManager {

    private final Logger logger;
    private final EconomyEngine economyEngine;
    private final ShopFavoriteManager shopFavoriteManager;
    private final JobMissionManager jobMissionManager;

    /**
     * Creates the player data manager.
     *
     * @param logger              plugin logger
     * @param economyEngine       economy engine owning account load/unload
     * @param shopFavoriteManager shop favorites, cleared on quit
     * @param jobMissionManager   mission manager, used to refresh stale missions on join
     */
    public PlayerDataManager(Logger logger, EconomyEngine economyEngine, ShopFavoriteManager shopFavoriteManager,
                              JobMissionManager jobMissionManager) {
        this.logger = logger;
        this.economyEngine = economyEngine;
        this.shopFavoriteManager = shopFavoriteManager;
        this.jobMissionManager = jobMissionManager;
    }

    /**
     * Handles a player join: loads their economy account and refreshes
     * any stale daily/weekly job missions.
     *
     * @param player the joining player
     */
    public void handleJoin(Player player) {
        economyEngine.loadAccount(player.getUniqueId(), player.getName());
        refreshStaleMissions(player);
    }

    private void refreshStaleMissions(Player player) {
        for (JobType type : JobType.values()) {
            try {
                boolean dailyStale = jobMissionManager.getActiveMissions(player.getUniqueId()).stream()
                        .filter(mission -> mission.jobType() == type && mission.period().equals(JobMissionManager.PERIOD_DAILY))
                        .allMatch(mission -> TimeUtils.isBeforeToday(mission.assignedAt()));

                if (dailyStale) {
                    jobMissionManager.assignDailyMissions(player.getUniqueId(), type);
                }

                boolean weeklyStale = jobMissionManager.getActiveMissions(player.getUniqueId()).stream()
                        .filter(mission -> mission.jobType() == type && mission.period().equals(JobMissionManager.PERIOD_WEEKLY))
                        .allMatch(mission -> TimeUtils.isBeforeThisWeek(mission.assignedAt()));

                if (weeklyStale) {
                    jobMissionManager.assignWeeklyMissions(player.getUniqueId(), type);
                }
            } catch (SQLException exception) {
                logger.warning("[EcoCore] Failed to refresh missions for "
                        + player.getUniqueId() + "/" + type + ": " + exception.getMessage());
            }
        }
    }

    /**
     * Handles a player quit: saves and unloads their economy account,
     * and clears session-only state from every module that keeps it.
     *
     * @param player the quitting player
     */
    public void handleQuit(Player player) {
        economyEngine.unloadAccount(player.getUniqueId());
        shopFavoriteManager.clear(player.getUniqueId());
    }
}
