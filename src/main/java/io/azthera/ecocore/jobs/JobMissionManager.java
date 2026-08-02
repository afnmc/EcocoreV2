package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.database.dao.JobMissionDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.JobMissionRecord;
import io.azthera.ecocore.model.JobType;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Assigns and tracks daily/weekly job missions. Every mission uses a
 * single generic template ("perform N actions in this job") tracked
 * via {@link #recordActionForMissions(UUID, JobType, int, double)},
 * which is called by {@code JobsManager} alongside normal xp/money
 * rewards whenever a job action completes.
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

    /**
     * Creates a job mission manager.
     *
     * @param jobMissionDao DAO for mission persistence
     * @param jobsConfig    resolved jobs.yml configuration (mission counts)
     * @param economyEngine economy engine used to pay mission completion rewards
     */
    public JobMissionManager(JobMissionDao jobMissionDao, JobsConfig jobsConfig, EconomyEngine economyEngine) {
        this.jobMissionDao = jobMissionDao;
        this.jobsConfig = jobsConfig;
        this.economyEngine = economyEngine;
    }

    /**
     * Assigns a fresh set of daily missions for a player's job, clearing
     * any previous daily missions for that job first.
     *
     * @param playerUuid the player's uuid
     * @param jobType    the job to assign missions for
     * @throws SQLException if the underlying persistence fails
     */
    public void assignDailyMissions(UUID playerUuid, JobType jobType) throws SQLException {
        assignMissions(playerUuid, jobType, PERIOD_DAILY, jobsConfig.getDailyMissionCount(),
                DAILY_TARGET_MIN, DAILY_TARGET_MAX);
    }

    /**
     * Assigns a fresh set of weekly missions for a player's job, clearing
     * any previous weekly missions for that job first.
     *
     * @param playerUuid the player's uuid
     * @param jobType    the job to assign missions for
     * @throws SQLException if the underlying persistence fails
     */
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

    /**
     * Returns every active (incomplete) mission currently assigned to a player.
     *
     * @param playerUuid the player's uuid
     * @return the player's active missions
     * @throws SQLException if the query fails
     */
    public List<JobMissionRecord> getActiveMissions(UUID playerUuid) throws SQLException {
        return jobMissionDao.findActiveForPlayer(playerUuid);
    }

    /**
     * Records that a player performed one job action, advancing every
     * active mission for that job by the given weight and paying out a
     * reward for any mission that reaches its target as a result.
     *
     * @param playerUuid the acting player's uuid
     * @param jobType    the job the action belongs to
     * @param weight     how much this single action counts toward mission progress (usually 1)
     * @param moneyScale a scale factor applied to mission completion rewards (e.g. the current job-bonus multiplier)
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
            }
        }
    }

    /**
     * Deletes stale missions of a given period older than the given
     * cutoff, called before reassigning a fresh batch.
     *
     * @param period       "DAILY" or "WEEKLY"
     * @param beforeMillis exclusive upper bound epoch millis
     * @throws SQLException if the delete fails
     */
    public void pruneOldMissions(String period, long beforeMillis) throws SQLException {
        jobMissionDao.deleteForPeriodBefore(period, beforeMillis);
    }
}