package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.database.dao.JobsDao;
import io.azthera.ecocore.model.JobData;

import java.sql.SQLException;

/**
 * Handles prestiging a job: resetting level/xp back to 1 in exchange
 * for a permanent xp-multiplier bonus, once a player reaches the
 * configured max level.
 */
public final class JobPrestigeManager {

    private final JobsDao jobsDao;
    private final JobsConfig jobsConfig;

    /**
     * Creates a job prestige manager.
     *
     * @param jobsDao    DAO for reading/writing job progress
     * @param jobsConfig resolved jobs.yml configuration
     */
    public JobPrestigeManager(JobsDao jobsDao, JobsConfig jobsConfig) {
        this.jobsDao = jobsDao;
        this.jobsConfig = jobsConfig;
    }

    /**
     * Checks whether a player's job progress is currently eligible to prestige.
     *
     * @param data the player's job progress
     * @return {@code true} if prestiging is currently allowed
     */
    public boolean canPrestige(JobData data) {
        return jobsConfig.isPrestigeEnabled()
                && data.getLevel() >= jobsConfig.getMaxLevel()
                && data.getPrestige() < jobsConfig.getPrestigeMax();
    }

    /**
     * Prestiges a player's job progress if eligible: increments prestige,
     * resets level to 1 and xp to 0, and persists the change.
     *
     * @param data the player's job progress to prestige
     * @return {@code true} if the prestige was applied
     * @throws SQLException if the underlying persistence fails
     */
    public boolean prestige(JobData data) throws SQLException {
        if (!canPrestige(data)) {
            return false;
        }

        data.setPrestige(data.getPrestige() + 1);
        data.setLevel(1);
        data.setXp(0);
        jobsDao.upsert(data);
        return true;
    }
}