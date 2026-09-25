package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Base implementation of {@link JobHandler} backed by a static
 * action-key-to-multiplier map, so each concrete job type class only
 * needs to declare its type and its action table.
 */
public abstract class AbstractJobHandler implements JobHandler {

    private final JobType type;
    private final Map<String, Double> actionMultipliers;

    /**
     * Creates a handler for the given job type.
     *
     * @param type              the job type this handler implements
     * @param actionMultipliers map of action key to reward multiplier
     */
    protected AbstractJobHandler(JobType type, Map<String, Double> actionMultipliers) {
        this.type = type;
        this.actionMultipliers = actionMultipliers;
    }

    @Override
    public JobType getType() {
        return type;
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return actionMultipliers.containsKey(actionKey);
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        return actionMultipliers.getOrDefault(actionKey, 0.0);
    }
}