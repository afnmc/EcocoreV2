package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.database.dao.JobsDao;
import io.azthera.ecocore.jobs.types.AlchemyJob;
import io.azthera.ecocore.jobs.types.BlacksmithJob;
import io.azthera.ecocore.jobs.types.BreederJob;
import io.azthera.ecocore.jobs.types.BuilderJob;
import io.azthera.ecocore.jobs.types.CookJob;
import io.azthera.ecocore.jobs.types.CrafterJob;
import io.azthera.ecocore.jobs.types.EnchanterJob;
import io.azthera.ecocore.jobs.types.ExcavatorJob;
import io.azthera.ecocore.jobs.types.ExplorerJob;
import io.azthera.ecocore.jobs.types.FarmerJob;
import io.azthera.ecocore.jobs.types.FishermanJob;
import io.azthera.ecocore.jobs.types.HunterJob;
import io.azthera.ecocore.jobs.types.JobHandler;
import io.azthera.ecocore.jobs.types.MerchantJob;
import io.azthera.ecocore.jobs.types.MinerJob;
import io.azthera.ecocore.jobs.types.QuestHunterJob;
import io.azthera.ecocore.jobs.types.WoodcutterJob;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Top-level facade for EcoCore's Jobs system: owns every
 * {@link JobHandler}, join/leave logic, and wires together
 * {@link JobProgressTracker}, {@link JobSkillTreeManager},
 * {@link JobMissionManager}, {@link JobPrestigeManager}, and
 * {@link JobLeaderboardManager}. Listener classes call
 * {@link #processAction(UUID, String, double)} whenever a
 * job-relevant Bukkit event occurs; this class fans that action out
 * to every job the player has joined that recognizes it.
 */
public final class JobsManager {

    private final Logger logger;
    private final JobsDao jobsDao;
    private final JobsConfig jobsConfig;

    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    private final JobProgressTracker progressTracker;
    private final JobSkillTreeManager skillTreeManager;
    private final JobMissionManager missionManager;
    private final JobPrestigeManager prestigeManager;
    private final JobLeaderboardManager leaderboardManager;

    /**
     * Creates the jobs manager and every job-type handler.
     *
     * @param logger             plugin logger
     * @param jobsDao            DAO for job progress persistence
     * @param jobsConfig         resolved jobs.yml configuration
     * @param progressTracker    tracker applying actions to xp/money/level
     * @param skillTreeManager   skill tree generator/evaluator
     * @param missionManager     mission assignment/tracking manager
     * @param prestigeManager    prestige eligibility/execution manager
     * @param leaderboardManager cached per-job leaderboard manager
     */
    public JobsManager(Logger logger, JobsDao jobsDao, JobsConfig jobsConfig,
                        JobProgressTracker progressTracker, JobSkillTreeManager skillTreeManager,
                        JobMissionManager missionManager, JobPrestigeManager prestigeManager,
                        JobLeaderboardManager leaderboardManager) {
        this.logger = logger;
        this.jobsDao = jobsDao;
        this.jobsConfig = jobsConfig;
        this.progressTracker = progressTracker;
        this.skillTreeManager = skillTreeManager;
        this.missionManager = missionManager;
        this.prestigeManager = prestigeManager;
        this.leaderboardManager = leaderboardManager;

        registerHandlers();
    }

    private void registerHandlers() {
        handlers.put(JobType.MINER, new MinerJob());
        handlers.put(JobType.WOODCUTTER, new WoodcutterJob());
        handlers.put(JobType.FARMER, new FarmerJob());
        handlers.put(JobType.HUNTER, new HunterJob());
        handlers.put(JobType.FISHERMAN, new FishermanJob());
        handlers.put(JobType.EXCAVATOR, new ExcavatorJob());
        handlers.put(JobType.BUILDER, new BuilderJob());
        handlers.put(JobType.CRAFTER, new CrafterJob());
        handlers.put(JobType.EXPLORER, new ExplorerJob());
        handlers.put(JobType.BREEDER, new BreederJob());
        handlers.put(JobType.COOK, new CookJob());
        handlers.put(JobType.BLACKSMITH, new BlacksmithJob());
        handlers.put(JobType.ENCHANTER, new EnchanterJob());
        handlers.put(JobType.ALCHEMY, new AlchemyJob());
        handlers.put(JobType.MERCHANT, new MerchantJob());
        handlers.put(JobType.QUEST_HUNTER, new QuestHunterJob());
    }

    public JobHandler getHandler(JobType type) {
        return handlers.get(type);
    }

    public Map<JobType, JobHandler> getAllHandlers() {
        return handlers;
    }

    /**
     * Whether a player has joined a given job.
     *
     * @param playerUuid the player's uuid
     * @param type       the job type
     * @return {@code true} if joined
     * @throws SQLException if the query fails
     */
    public boolean hasJoined(UUID playerUuid, JobType type) throws SQLException {
        return jobsDao.find(playerUuid, type) != null;
    }

    /**
     * Joins a player to a job at level 1, if not already joined, and
     * assigns their first batch of daily missions.
     *
     * @param playerUuid the player's uuid
     * @param type       the job type to join
     * @return {@code true} if newly joined, {@code false} if already joined
     * @throws SQLException if the underlying persistence fails
     */
    public boolean join(UUID playerUuid, JobType type) throws SQLException {
        if (hasJoined(playerUuid, type)) {
            return false;
        }
        JobData data = new JobData(playerUuid, type, 0L, 1, 0, System.currentTimeMillis());
        jobsDao.upsert(data);
        missionManager.assignDailyMissions(playerUuid, type);
        return true;
    }

    /**
     * Returns a player's progress in a single job.
     *
     * @param playerUuid the player's uuid
     * @param type       the job type
     * @return the job data, or {@code null} if not joined
     * @throws SQLException if the query fails
     */
    public JobData getProgress(UUID playerUuid, JobType type) throws SQLException {
        return jobsDao.find(playerUuid, type);
    }

    /**
     * Returns every job a player has joined.
     *
     * @param playerUuid the player's uuid
     * @return the player's joined jobs
     * @throws SQLException if the query fails
     */
    public List<JobData> getAllProgress(UUID playerUuid) throws SQLException {
        return jobsDao.findAllForPlayer(playerUuid);
    }

    /**
     * Processes a single in-game action for a player across every job
     * they've joined that recognizes it (an action key can only ever
     * match one job in EcoCore's default handlers, but this stays
     * generic in case a server owner adds overlapping custom handlers).
     *
     * @param playerUuid         the acting player's uuid
     * @param actionKey          the action that occurred (e.g. "BREAK_DIAMOND_ORE")
     * @param jobBonusMultiplier the current economic state's job-bonus multiplier
     */
    public void processAction(UUID playerUuid, String actionKey, double jobBonusMultiplier) {
        for (JobHandler handler : handlers.values()) {
            if (!handler.appliesTo(actionKey)) {
                continue;
            }
            try {
                JobProgressTracker.ActionResult result = progressTracker.applyAction(
                        playerUuid, handler, actionKey, jobBonusMultiplier);
                if (result != null) {
                    missionManager.recordActionForMissions(playerUuid, handler.getType(), 1, jobBonusMultiplier);
                }
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to process job action " + actionKey
                        + " for " + playerUuid + ": " + exception.getMessage());
            }
        }
    }

    public JobSkillTreeManager getSkillTreeManager() {
        return skillTreeManager;
    }

    public JobMissionManager getMissionManager() {
        return missionManager;
    }

    public JobPrestigeManager getPrestigeManager() {
        return prestigeManager;
    }

    public JobLeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public JobsConfig getJobsConfig() {
        return jobsConfig;
    }
}