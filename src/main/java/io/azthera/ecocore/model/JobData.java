package io.azthera.ecocore.model;

import java.util.UUID;

/**
 * Tracks a single player's progress in a single job.
 * Mirrors a row in the {@code jobs_data} table, keyed by
 * (player uuid, job type).
 */
public final class JobData {

    private final UUID playerUuid;
    private final JobType jobType;
    private long xp;
    private int level;
    private int prestige;
    private long updatedAt;

    /**
     * Creates job progress data for a player.
     *
     * @param playerUuid the player this data belongs to
     * @param jobType    the job type
     * @param xp         current xp within the current level/prestige
     * @param level      current job level
     * @param prestige   current prestige tier, 0 if never prestiged
     * @param updatedAt  epoch millis of the last update
     */
    public JobData(UUID playerUuid, JobType jobType, long xp, int level, int prestige, long updatedAt) {
        this.playerUuid = playerUuid;
        this.jobType = jobType;
        this.xp = xp;
        this.level = level;
        this.prestige = prestige;
        this.updatedAt = updatedAt;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public JobType getJobType() {
        return jobType;
    }

    public long getXp() {
        return xp;
    }

    /**
     * Adds xp to this job's progress and refreshes the updated-at timestamp.
     * Does not itself perform level-up logic; that is handled by
     * {@code JobProgressTracker} so leaderboard/mission side effects fire correctly.
     *
     * @param amount xp to add, must be non-negative
     */
    public void addXp(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("XP amount cannot be negative");
        }
        this.xp += amount;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setXp(long xp) {
        this.xp = xp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}