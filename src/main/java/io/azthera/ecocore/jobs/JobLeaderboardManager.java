package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.database.dao.JobsDao;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Caches per-job leaderboards, refreshing from the database no more
 * often than the interval configured in {@code jobs.yml} so
 * repeatedly opening {@code JobLeaderboardGui} doesn't hammer SQLite.
 */
public final class JobLeaderboardManager {

    private final JobsDao jobsDao;
    private final JobsConfig jobsConfig;

    private final Map<JobType, List<JobData>> cache = new EnumMap<>(JobType.class);
    private final Map<JobType, Long> lastRefresh = new EnumMap<>(JobType.class);

    /**
     * Creates a leaderboard manager.
     *
     * @param jobsDao    DAO used to query top players per job
     * @param jobsConfig resolved jobs.yml configuration (top size, refresh interval)
     */
    public JobLeaderboardManager(JobsDao jobsDao, JobsConfig jobsConfig) {
        this.jobsDao = jobsDao;
        this.jobsConfig = jobsConfig;
    }

    /**
     * Returns the cached (or freshly loaded, if stale) leaderboard for a job.
     *
     * @param type the job type
     * @return the top players for this job, highest ranked first
     * @throws SQLException if a refresh is needed and the underlying query fails
     */
    public List<JobData> getLeaderboard(JobType type) throws SQLException {
        long now = System.currentTimeMillis();
        Long last = lastRefresh.get(type);
        long maxAgeMillis = jobsConfig.getLeaderboardRefreshSeconds() * 1000L;

        if (last == null || (now - last) >= maxAgeMillis || !cache.containsKey(type)) {
            List<JobData> fresh = jobsDao.topByJob(type, jobsConfig.getLeaderboardTopSize());
            cache.put(type, fresh);
            lastRefresh.put(type, now);
        }

        return cache.get(type);
    }

    /**
     * Forces the next {@link #getLeaderboard(JobType)} call for a job to
     * bypass the cache and re-query the database.
     *
     * @param type the job type to invalidate
     */
    public void invalidate(JobType type) {
        lastRefresh.remove(type);
    }
}