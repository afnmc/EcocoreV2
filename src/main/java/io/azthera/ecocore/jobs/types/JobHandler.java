package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

/**
 * Defines how a single job type responds to in-game actions. Each
 * concrete handler (one per {@link JobType}) declares which action
 * keys it cares about (e.g. "BREAK_DIAMOND_ORE" for {@code MinerJob})
 * and how strongly each one should count toward money/xp rewards.
 *
 * <p>Action keys are plain strings rather than an enum so listener
 * classes (block break, entity death, fishing, crafting, etc.) can
 * report fine-grained events without every job type needing to know
 * about every possible Bukkit event type.
 */
public interface JobHandler {

    /**
     * The job type this handler implements.
     *
     * @return the job type
     */
    JobType getType();

    /**
     * Whether this job type awards anything for the given action.
     *
     * @param actionKey the action that occurred (e.g. "BREAK_IRON_ORE")
     * @return {@code true} if this job type responds to the action
     */
    boolean appliesTo(String actionKey);

    /**
     * The reward multiplier for a given action, applied against the
     * job's base money/xp-per-action values from {@code jobs.yml}.
     * A value of 0 means the action does not apply to this job.
     *
     * @param actionKey the action that occurred
     * @return the multiplier, 0.0 if not applicable
     */
    double getRewardMultiplier(String actionKey);
}