package io.azthera.ecocore.model;

import java.util.UUID;

/**
 * An immutable record of a single assigned job mission (daily or weekly).
 * Mirrors a row in the {@code job_missions} table.
 *
 * @param id          database row id
 * @param playerUuid  the player this mission was assigned to
 * @param jobType     the job this mission belongs to
 * @param missionKey  a short key identifying the mission template (e.g. "ACTION_COUNT")
 * @param period      "DAILY" or "WEEKLY"
 * @param progress    current progress toward the target
 * @param target      the progress value required to complete this mission
 * @param completed   whether this mission has already been completed and rewarded
 * @param assignedAt  epoch millis when this mission was assigned
 */
public record JobMissionRecord(long id, UUID playerUuid, JobType jobType, String missionKey,
                                String period, int progress, int target, boolean completed, long assignedAt) {

    /**
     * Whether this mission's progress has reached its target, regardless
     * of whether the {@code completed} flag has been persisted yet.
     *
     * @return {@code true} if progress meets or exceeds the target
     */
    public boolean isComplete() {
        return completed || progress >= target;
    }
}