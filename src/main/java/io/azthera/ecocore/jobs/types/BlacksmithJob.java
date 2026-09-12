package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards ore/metal smelting and anvil operations.
 */
public final class BlacksmithJob extends AbstractJobHandler {

    private static final Map<String, Double> ORE_OUTPUTS = Map.of(
            "IRON_INGOT", 1.2,
            "GOLD_INGOT", 1.4,
            "COPPER_INGOT", 1.1,
            "NETHERITE_SCRAP", 1.8
    );

    public BlacksmithJob() {
        super(JobType.BLACKSMITH, Map.of());
    }

    @Override
    public boolean appliesTo(String actionKey) {
        if (actionKey == null) {
            return false;
        }
        if (actionKey.startsWith("ANVIL_")) {
            return true;
        }
        return actionKey.startsWith("SMELT_")
                && ORE_OUTPUTS.containsKey(actionKey.substring("SMELT_".length()));
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey != null && actionKey.startsWith("ANVIL_")) {
            return 1.8;
        }
        if (actionKey != null && actionKey.startsWith("SMELT_")) {
            return ORE_OUTPUTS.getOrDefault(
                    actionKey.substring("SMELT_".length()), 0.0);
        }
        return 0.0;
    }
}
