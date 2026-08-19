package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.database.dao.JobsDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.jobs.types.JobHandler;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Applies a single completed job action to a player's progress:
 * computes money/xp from the job's base values, the handler's
 * reward multiplier, and the current economic job-bonus multiplier;
 * handles level-ups against the configured xp curve; and persists
 * the result.
 */
public final class JobProgressTracker {

    private final JobsDao jobsDao;
    private final JobsConfig jobsConfig;
    private final EconomyEngine economyEngine;

    /**
     * The outcome of applying a single job action.
     *
     * @param leveledUp    whether this action caused one or more level-ups
     * @param newLevel     the player's resulting level in this job
     * @param moneyAwarded the money paid out for this action
     * @param xpAwarded    the xp gained for this action
     */
    public record ActionResult(boolean leveledUp, int newLevel, double moneyAwarded, long xpAwarded) {
    }

    /**
     * Creates a job progress tracker.
     *
     * @param jobsDao       DAO for reading/writing job progress
     * @param jobsConfig    resolved jobs.yml configuration
     * @param economyEngine economy engine used to pay job rewards
     */
    public JobProgressTracker(JobsDao jobsDao, JobsConfig jobsConfig, EconomyEngine economyEngine) {
        this.jobsDao = jobsDao;
        this.jobsConfig = jobsConfig;
        this.economyEngine = economyEngine;
    }

    /**
     * Applies a completed action to a player's progress in a job, if
     * they have joined it and the handler recognizes the action.
     *
     * @param playerUuid         the acting player's uuid
     * @param handler            the job handler evaluating this action
     * @param actionKey          the action that occurred (e.g. "BREAK_DIAMOND_ORE")
     * @param jobBonusMultiplier the current economic state's job-bonus multiplier
     * @return the result of applying the action, or {@code null} if not
     *         applicable (player hasn't joined this job, or the action
     *         doesn't apply to it)
     * @throws SQLException if the underlying persistence fails
     */
    public ActionResult applyAction(UUID playerUuid, JobHandler handler, String actionKey,
                                     double jobBonusMultiplier) throws SQLException {
        double multiplier = handler.getRewardMultiplier(actionKey);
        if (multiplier <= 0.0) {
            return null;
        }

        JobType type = handler.getType();
        JobData data = jobsDao.find(playerUuid, type);
        if (data == null) {
            return null;
        }

        JobsConfig.JobDefinition definition = jobsConfig.getDefinition(type);
        if (definition == null) {
            return null;
        }

        double prestigeBonus = 1.0 + (data.getPrestige() * jobsConfig.getPrestigeXpMultiplierBonus());

        double money = definition.baseMoneyPerAction() * multiplier * jobBonusMultiplier * prestigeBonus;
        long xpGain = Math.round(definition.baseXpPerAction() * multiplier * jobBonusMultiplier * prestigeBonus);

        int levelBefore = data.getLevel();
        data.addXp(xpGain);

        int level = data.getLevel();
        while (level < jobsConfig.getMaxLevel()
                && data.getXp() >= jobsConfig.xpRequiredForLevel(level + 1)) {
            level++;
        }
        data.setLevel(level);

        jobsDao.upsert(data);

        if (money > 0) {
            economyEngine.deposit(playerUuid, money, TransactionLogger.REASON_JOB_REWARD);
        }

        return new ActionResult(level > levelBefore, level, money, xpGain);
    }
}