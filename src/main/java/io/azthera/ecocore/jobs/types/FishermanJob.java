package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards every successful fishing catch. The catch material is carried in
 * the action key so missions can target COD, SALMON, treasure, etc.
 */
public final class FishermanJob extends AbstractJobHandler {

    private static final Map<String, Double> MULTIPLIERS = Map.ofEntries(
            Map.entry("COD", 1.0),
            Map.entry("SALMON", 1.1),
            Map.entry("PUFFERFISH", 1.3),
            Map.entry("TROPICAL_FISH", 1.4)
    );

    public FishermanJob() {
        super(JobType.FISHERMAN, Map.of());
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return actionKey != null && actionKey.startsWith("CATCH_")
                && actionKey.length() > "CATCH_".length();
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (!appliesTo(actionKey)) {
            return 0.0;
        }
        String target = actionKey.substring("CATCH_".length());
        if ("TREASURE".equals(target)) {
            return 3.0;
        }
        if ("JUNK".equals(target)) {
            return 0.3;
        }
        return MULTIPLIERS.getOrDefault(target, 0.5);
    }
}
