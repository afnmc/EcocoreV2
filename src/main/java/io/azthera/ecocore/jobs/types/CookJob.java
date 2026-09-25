package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards actual furnace/smoker/blast-furnace cooking results.
 *
 * <p>Cooking is represented by action keys such as
 * {@code SMELT_COOKED_BEEF}. The result item is what the player actually
 * takes from the furnace, so a Cook mission never treats crafting a cooked
 * food item as cooking.</p>
 */
public final class CookJob extends AbstractJobHandler {

    private static final Map<String, Double> FOOD_MULTIPLIERS = Map.ofEntries(
            Map.entry("COOKED_BEEF", 0.7),
            Map.entry("COOKED_CHICKEN", 0.7),
            Map.entry("COOKED_PORKCHOP", 0.7),
            Map.entry("COOKED_MUTTON", 0.7),
            Map.entry("COOKED_RABBIT", 0.7),
            Map.entry("COOKED_COD", 0.7),
            Map.entry("COOKED_SALMON", 0.8),
            Map.entry("BAKED_POTATO", 0.9)
    );

    public CookJob() {
        super(JobType.COOK, Map.of());
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return actionKey != null
                && actionKey.startsWith("SMELT_")
                && FOOD_MULTIPLIERS.containsKey(actionKey.substring("SMELT_".length()));
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (!appliesTo(actionKey)) {
            return 0.0;
        }
        return FOOD_MULTIPLIERS.getOrDefault(actionKey.substring("SMELT_".length()), 0.0);
    }
}
