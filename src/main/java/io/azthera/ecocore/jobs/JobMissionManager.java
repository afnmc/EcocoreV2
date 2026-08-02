package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.database.dao.JobMissionDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.JobMissionRecord;
import io.azthera.ecocore.model.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Assigns and tracks daily/weekly job missions, and notifies the
 * player in chat (with a reward summary and sound) the moment a
 * mission is completed.
 */
public final class JobMissionManager {

    public static final String PERIOD_DAILY = "DAILY";
    public static final String PERIOD_WEEKLY = "WEEKLY";
    private static final String TEMPLATE_ACTION_COUNT = "ACTION_COUNT";

    private static final int DAILY_TARGET_MIN = 20;
    private static final int DAILY_TARGET_MAX = 60;
    private static final int WEEKLY_TARGET_MIN = 150;
    private static final int WEEKLY_TARGET_MAX = 400;
    private static final double MISSION_REWARD_PER_TARGET_UNIT = 2.0;

    private final JobMissionDao jobMissionDao;
    private final JobsConfig jobsConfig;
    private final EconomyEngine economyEngine;
    private final MessagesConfig messagesConfig;

    /**
     * Creates a job mission manager.
     *
     * @param jobMissionDao  DAO for mission persistence
     * @param jobsConfig     resolved jobs.yml configuration (mission counts)
     * @param economyEngine  economy engine used to pay mission completion rewards
     * @param messagesConfig resolved messages.yml configuration, used for completion notifications
     */
    public JobMissionManager(JobMissionDao jobMissionDao, JobsConfig jobsConfig,
                              EconomyEngine economyEngine, MessagesConfig messagesConfig) {
        this.jobMissionDao = jobMissionDao;
        this.jobsConfig = jobsConfig;
        this.economyEngine = economyEngine;
        this.messagesConfig = messagesConfig;
    }

    public void assignDailyMissions(UUID playerUuid, JobType jobType) throws SQLException {
        assignMissions(playerUuid, jobType, PERIOD_DAILY, jobsConfig.getDailyMissionCount(),
                DAILY_TARGET_MIN, DAILY_TARGET_MAX);
    }

    public void assignWeeklyMissions(UUID playerUuid, JobType jobType) throws SQLException {
        assignMissions(playerUuid, jobType, PERIOD_WEEKLY, jobsConfig.getWeeklyMissionCount(),
                WEEKLY_TARGET_MIN, WEEKLY_TARGET_MAX);
    }

    private void assignMissions(UUID playerUuid, JobType jobType, String period, int count,
                                 int targetMin, int targetMax) throws SQLException {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            int target = ThreadLocalRandom.current().nextInt(targetMin, targetMax + 1);
            jobMissionDao.insert(playerUuid, jobType, TEMPLATE_ACTION_COUNT, period, target, now);
        }
    }

    public List<JobMissionRecord> getActiveMissions(UUID playerUuid) throws SQLException {
        return jobMissionDao.findActiveForPlayer(playerUuid);
    }

    /**
     * Records that a player performed one job action, advancing every
     * active mission for that job, paying out and notifying the
     * player for any mission that reaches its target as a result.
     *
     * @param playerUuid the acting player's uuid
     * @param jobType    the job the action belongs to
     * @param weight     how much this action counts toward mission progress
     * @param moneyScale a scale factor applied to mission completion rewards
     * @throws SQLException if the underlying persistence fails
     */
    public void recordActionForMissions(UUID playerUuid, JobType jobType, int weight, double moneyScale) throws SQLException {
        List<JobMissionRecord> active = jobMissionDao.findActiveForPlayerAndJob(playerUuid, jobType);

        for (JobMissionRecord mission : active) {
            int newProgress = Math.min(mission.target(), mission.progress() + weight);
            boolean nowComplete = newProgress >= mission.target();

            jobMissionDao.updateProgress(mission.id(), newProgress, nowComplete);

            if (nowComplete && !mission.completed()) {
                double reward = mission.target() * MISSION_REWARD_PER_TARGET_UNIT * moneyScale;
                economyEngine.deposit(playerUuid, reward, TransactionLogger.REASON_MISSION_REWARD);
                notifyMissionComplete(playerUuid, mission, reward);
            }
        }
    }

    private void notifyMissionComplete(UUID playerUuid, JobMissionRecord mission, double reward) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return;
        }
        String periodLabel = mission.period().equals(PERIOD_DAILY) ? "Harian" : "Mingguan";
        player.sendMessage(messagesConfig.getWithPrefix("jobs.mission-complete",
                "period", periodLabel, "reward", String.format("%.2f", reward)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
    }

    public void pruneOldMissions(String period, long beforeMillis) throws SQLException {
        jobMissionDao.deleteForPeriodBefore(period, beforeMillis);
    }
        }
