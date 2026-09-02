package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.config.MessagesConfig;
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
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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
 * to every job the player has joined that recognizes it, and sends a
 * level-up notification when applicable.
 */
public final class JobsManager {

    private final Logger logger;
    private final JobsDao jobsDao;
    private final JobsConfig jobsConfig;
    private final MessagesConfig messagesConfig;

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
     * @param messagesConfig     resolved messages.yml configuration, used for level-up notifications
     */
    public JobsManager(Logger logger, JobsDao jobsDao, JobsConfig jobsConfig,
                        JobProgressTracker progressTracker, JobSkillTreeManager skillTreeManager,
                        JobMissionManager missionManager, JobPrestigeManager prestigeManager,
                        JobLeaderboardManager leaderboardManager, MessagesConfig messagesConfig) {
        this.logger = logger;
        this.jobsDao = jobsDao;
        this.jobsConfig = jobsConfig;
        this.progressTracker = progressTracker;
        this.skillTreeManager = skillTreeManager;
        this.missionManager = missionManager;
        this.prestigeManager = prestigeManager;
        this.leaderboardManager = leaderboardManager;
        this.messagesConfig = messagesConfig;

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

    public boolean hasJoined(UUID playerUuid, JobType type) throws SQLException {
        return jobsDao.find(playerUuid, type) != null;
    }

    public boolean join(UUID playerUuid, JobType type) throws SQLException {
        if (hasJoined(playerUuid, type)) {
            return false;
        }
        JobData data = new JobData(playerUuid, type, 0L, 1, 0, System.currentTimeMillis());
        jobsDao.upsert(data);
        missionManager.assignDailyMissions(playerUuid, type);
        return true;
    }

    public JobData getProgress(UUID playerUuid, JobType type) throws SQLException {
        return jobsDao.find(playerUuid, type);
    }

    public List<JobData> getAllProgress(UUID playerUuid) throws SQLException {
        return jobsDao.findAllForPlayer(playerUuid);
    }

    /**
     * Processes a single in-game action for a player across every job
     * they've joined that recognizes it, and sends a level-up chat
     * message + sound when the action pushes them to a new level.
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
                    io.azthera.ecocore.model.MissionType missionType = mapActionKeyToMissionType(actionKey);
                    String missionTarget = mapActionKeyToTarget(actionKey);
                    if (missionType != null) {
                        missionManager.recordActionForMissions(
                                playerUuid, handler.getType(), missionType, missionTarget, 1, jobBonusMultiplier);
                    } else {
                        missionManager.recordActionForMissions(playerUuid, handler.getType(), 1, jobBonusMultiplier);
                    }

                    if (result.leveledUp()) {
                        notifyLevelUp(playerUuid, handler.getType(), result.newLevel());
                    }
                }
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to process job action " + actionKey
                        + " for " + playerUuid + ": " + exception.getMessage());
            }
        }
    }

    /**
     * Maps a job action key (e.g. "BREAK_DIAMOND_ORE", "HARVEST_WHEAT")
     * to its corresponding {@link io.azthera.ecocore.model.MissionType}
     * (Revisi 18), following the VERB_TARGET convention used
     * throughout the job handlers.
     *
     * @param actionKey the raw action key from a job handler
     * @return the corresponding mission type, or {@code null} if unrecognized
     */
    private io.azthera.ecocore.model.MissionType mapActionKeyToMissionType(String actionKey) {
        if (actionKey == null) {
            return null;
        }
        String upper = actionKey.toUpperCase();
        if (upper.startsWith("BREAK_")) {
            if (upper.contains("ORE")) {
                return io.azthera.ecocore.model.MissionType.MINE_ORE;
            }
            if (upper.contains("STONE") || upper.contains("DEEPSLATE")) {
                return io.azthera.ecocore.model.MissionType.MINE_STONE;
            }
            return io.azthera.ecocore.model.MissionType.BREAK_BLOCK;
        }
        if (upper.startsWith("PLACE_")) {
            return io.azthera.ecocore.model.MissionType.PLACE_BLOCK;
        }
        if (upper.startsWith("HARVEST_")) {
            return io.azthera.ecocore.model.MissionType.HARVEST_CROP;
        }
        if (upper.startsWith("PLANT_")) {
            return io.azthera.ecocore.model.MissionType.PLANT_CROP;
        }
        if (upper.startsWith("CHOP_")) {
            return io.azthera.ecocore.model.MissionType.CHOP_TREE;
        }
        if (upper.startsWith("COLLECT_EGG")) {
            return io.azthera.ecocore.model.MissionType.COLLECT_EGG;
        }
        if (upper.startsWith("COLLECT_")) {
            return io.azthera.ecocore.model.MissionType.COLLECT_ITEM;
        }
        if (upper.startsWith("CRAFT_")) {
            return io.azthera.ecocore.model.MissionType.CRAFT_ITEM;
        }
        if (upper.startsWith("SMELT_")) {
            return io.azthera.ecocore.model.MissionType.SMELT_ITEM;
        }
        if (upper.startsWith("FISH_")) {
            return io.azthera.ecocore.model.MissionType.FISH_ITEM;
        }
        if (upper.startsWith("KILL_")) {
            return io.azthera.ecocore.model.MissionType.KILL_MOB;
        }
        if (upper.startsWith("BREED_")) {
            return io.azthera.ecocore.model.MissionType.BREED_ANIMAL;
        }
        if (upper.startsWith("SHEAR_")) {
            return io.azthera.ecocore.model.MissionType.SHEAR_SHEEP;
        }
        if (upper.startsWith("MILK_")) {
            return io.azthera.ecocore.model.MissionType.MILK_COW;
        }
        if (upper.startsWith("TRADE_") || upper.equals("TRADE")) {
            return io.azthera.ecocore.model.MissionType.TRADE;
        }
        if (upper.startsWith("ENCHANT_") || upper.equals("ENCHANT")) {
            return io.azthera.ecocore.model.MissionType.ENCHANT;
        }
        if (upper.startsWith("BREW_")) {
            return io.azthera.ecocore.model.MissionType.BREW_POTION;
        }
        if (upper.startsWith("SELL_")) {
            return io.azthera.ecocore.model.MissionType.SELL_ITEM;
        }
        if (upper.startsWith("BUY_")) {
            return io.azthera.ecocore.model.MissionType.BUY_SHOP;
        }
        return null;
    }

    /**
     * Extracts the specific target material/entity/item from an
     * action key following the VERB_TARGET convention.
     *
     * @param actionKey the raw action key
     * @return the extracted target, or {@code null} if there's no target portion
     */
    private String mapActionKeyToTarget(String actionKey) {
        if (actionKey == null || !actionKey.contains("_")) {
            return null;
        }
        int firstUnderscore = actionKey.indexOf('_');
        String remainder = actionKey.substring(firstUnderscore + 1);
        return remainder.isBlank() ? null : remainder.toUpperCase();
    }

    private void notifyLevelUp(UUID playerUuid, JobType type, int newLevel) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return;
        }
        player.sendMessage(messagesConfig.getWithPrefix("jobs.level-up",
                "job", type.configKey(), "level", String.valueOf(newLevel)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
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