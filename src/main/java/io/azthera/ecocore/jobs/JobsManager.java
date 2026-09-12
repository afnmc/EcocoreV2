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
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final JavaPlugin plugin;
    private final JobsDao jobsDao;
    private final JobsConfig jobsConfig;
    private final MessagesConfig messagesConfig;

    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    private final JobProgressTracker progressTracker;
    private final JobSkillTreeManager skillTreeManager;
    private final JobMissionManager missionManager;
    private final JobPrestigeManager prestigeManager;
    private final JobLeaderboardManager leaderboardManager;

    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> playerQueues = new ConcurrentHashMap<>();

    /**
     * Creates the jobs manager and every job-type handler.
     *
     * @param logger             plugin logger
     * @param plugin             plugin instance used for async scheduling
     * @param jobsDao            DAO for job progress persistence
     * @param jobsConfig         resolved jobs.yml configuration
     * @param progressTracker    tracker applying actions to xp/money/level
     * @param skillTreeManager   skill tree generator/evaluator
     * @param missionManager     mission assignment/tracking manager
     * @param prestigeManager    prestige eligibility/execution manager
     * @param leaderboardManager cached per-job leaderboard manager
     * @param messagesConfig     resolved messages.yml configuration, used for level-up notifications
     */
    public JobsManager(Logger logger, JavaPlugin plugin, JobsDao jobsDao, JobsConfig jobsConfig,
                        JobProgressTracker progressTracker, JobSkillTreeManager skillTreeManager,
                        JobMissionManager missionManager, JobPrestigeManager prestigeManager,
                        JobLeaderboardManager leaderboardManager, MessagesConfig messagesConfig) {
        this.logger = logger;
        this.plugin = plugin;
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

    public Object getPlayerLock(UUID playerUuid) {
        return playerLocks.computeIfAbsent(playerUuid, u -> new Object());
    }

    public void cleanupPlayer(UUID playerUuid) {
        CompletableFuture<Void> queue = playerQueues.get(playerUuid);
        if (queue != null && !queue.isDone()) {
            queue.whenComplete((ignored, error) -> {
                playerQueues.remove(playerUuid, queue);
                playerLocks.remove(playerUuid);
            });
            return;
        }
        playerQueues.remove(playerUuid, queue);
        playerLocks.remove(playerUuid);
    }

    public CompletableFuture<Void> executeAsyncForPlayer(UUID playerUuid, Runnable task) {
        return playerQueues.compute(playerUuid, (uuid, previousFuture) -> {
            Runnable action = () -> {
                synchronized (getPlayerLock(playerUuid)) {
                    task.run();
                }
            };
            if (previousFuture == null || previousFuture.isDone()) {
                return CompletableFuture.runAsync(action, r -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, r));
            } else {
                return previousFuture.handle((res, ex) -> null)
                        .thenRunAsync(action, r -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, r));
            }
        });
    }

    public boolean join(UUID playerUuid, JobType type) throws SQLException {
        synchronized (getPlayerLock(playerUuid)) {
            if (hasJoined(playerUuid, type)) {
                return false;
            }
            JobData data = new JobData(playerUuid, type, 0L, 1, 0, System.currentTimeMillis());
            jobsDao.upsert(data);
            missionManager.assignDailyMissions(playerUuid, type);
            return true;
        }
    }

    /**
     * Removes a player's progress in a job (Revisi 20, used by the
     * {@code /jobs} "Leave Job" button): permanently deletes their
     * level/xp/prestige for that job and clears any missions still
     * assigned for it, so they start fresh if they join again.
     *
     * @param playerUuid the player's uuid
     * @param type       the job type to leave
     * @return {@code true} if they had joined and were removed, {@code false} if they hadn't joined
     * @throws SQLException if the underlying persistence fails
     */
    public boolean leave(UUID playerUuid, JobType type) throws SQLException {
        synchronized (getPlayerLock(playerUuid)) {
            if (!hasJoined(playerUuid, type)) {
                return false;
            }
            jobsDao.delete(playerUuid, type);
            missionManager.clearMissionsForJob(playerUuid, type);
            return true;
        }
    }

    public JobData getProgress(UUID playerUuid, JobType type) throws SQLException {
        synchronized (getPlayerLock(playerUuid)) {
            return jobsDao.find(playerUuid, type);
        }
    }

    public List<JobData> getAllProgress(UUID playerUuid) throws SQLException {
        synchronized (getPlayerLock(playerUuid)) {
            return jobsDao.findAllForPlayer(playerUuid);
        }
    }

    /**
     * Processes a single in-game action for a player across every job
     * they've joined that recognizes it, and sends a level-up chat
     * message + sound when the action pushes them to a new level.
     *
     * <p><strong>Thread safety:</strong> This method is called from Bukkit
     * event handlers on the main server thread. All SQLite I/O (job progress
     * reads/writes, mission updates, economy deposits) is serialized per player
     * and dispatched asynchronously via {@link org.bukkit.scheduler.BukkitScheduler#runTaskAsynchronously}
     * so the main thread is never blocked on JDBC and lost updates cannot occur.
     * Only the final level-up notification hops back to the main thread
     * via {@link org.bukkit.scheduler.BukkitScheduler#runTask}.
     *
     * @param playerUuid         the acting player's uuid
     * @param actionKey          the action that occurred (e.g. "BREAK_DIAMOND_ORE")
     * @param jobBonusMultiplier the current economic state's job-bonus multiplier
     */
    public void processAction(UUID playerUuid, String actionKey, double jobBonusMultiplier) {
        processAction(playerUuid, actionKey, jobBonusMultiplier, 1);
    }

    /**
     * Processes an action with an explicit quantity. Quantity is used both for
     * job reward scaling and mission progress, so bulk operations such as
     * selling 32 items or crafting 4 items do not count as only one action.
     */
    public void processAction(UUID playerUuid, String actionKey, double jobBonusMultiplier, int quantity) {
        if (actionKey == null || actionKey.isBlank()) {
            return;
        }

        int weight = Math.max(1, quantity);
        List<JobHandler> matchingHandlers = new ArrayList<>();
        for (JobHandler handler : handlers.values()) {
            if (handler.appliesTo(actionKey)) {
                matchingHandlers.add(handler);
            }
        }

        io.azthera.ecocore.model.MissionType missionType = mapActionKeyToMissionType(actionKey);
        if (matchingHandlers.isEmpty() && missionType == null) {
            return;
        }

        executeAsyncForPlayer(playerUuid, () -> {
            // Job rewards are only applied to handlers that explicitly recognize
            // this action. Mission tracking is deliberately independent: a mission
            // can exist for an action that does not award normal job XP/money
            // (for example EARN_MONEY for Quest Hunter).
            for (JobHandler handler : matchingHandlers) {
                try {
                    JobProgressTracker.ActionResult result = progressTracker.applyAction(
                            playerUuid, handler, actionKey, jobBonusMultiplier, weight);

                    if (result != null && result.leveledUp()) {
                        plugin.getServer().getScheduler().runTask(plugin,
                                () -> notifyLevelUp(playerUuid, handler.getType(), result.newLevel()));
                    }
                } catch (SQLException exception) {
                    logger.severe("[EcoCore] Failed to process job action " + actionKey
                            + " for " + playerUuid + ": " + exception.getMessage());
                }
            }

            if (missionType != null) {
                String missionTarget = mapActionKeyToTarget(actionKey);
                for (JobType jobType : JobType.values()) {
                    try {
                        missionManager.recordActionForMissions(
                                playerUuid, jobType, missionType, missionTarget, weight, jobBonusMultiplier);
                    } catch (SQLException exception) {
                        logger.severe("[EcoCore] Failed to process mission action " + actionKey
                                + " for " + playerUuid + "/" + jobType + ": " + exception.getMessage());
                    }
                }
            }
        });
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
        if (upper.startsWith("COOK_")) {
            return io.azthera.ecocore.model.MissionType.COOK_ITEM;
        }
        if (upper.startsWith("CATCH_")) {
            return io.azthera.ecocore.model.MissionType.FISH_ITEM;
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
        if (upper.startsWith("ANVIL_")) {
            return io.azthera.ecocore.model.MissionType.ANVIL_USE;
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
        if (actionKey == null || actionKey.isBlank()) {
            return null;
        }
        String upper = actionKey.toUpperCase();

        String[] prefixes = {
                "BREAK_", "PLACE_", "HARVEST_", "PLANT_", "CHOP_",
                "COLLECT_", "CRAFT_", "SMELT_", "CATCH_", "KILL_",
                "BREED_", "SHEAR_", "MILK_", "TRADE_", "ENCHANT_",
                "BREW_", "COOK_", "SELL_ITEM_", "BUY_ITEM_", "ANVIL_"
        };

        for (String prefix : prefixes) {
            if (upper.startsWith(prefix)) {
                String target = upper.substring(prefix.length());
                if (target.isBlank()) {
                    return null;
                }
                // Cooking actions are output-oriented. For example:
                // COOK_RAW_BEEF_TO_COOKED_BEEF -> COOKED_BEEF.
                if (prefix.equals("COOK_")) {
                    int toIndex = target.lastIndexOf("_TO_");
                    return toIndex >= 0 ? target.substring(toIndex + 4) : target;
                }
                return target;
            }
        }
        return null;
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
