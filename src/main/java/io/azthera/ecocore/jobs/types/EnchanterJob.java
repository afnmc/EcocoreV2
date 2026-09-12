package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards enchanting and anvil combining/repairing operations.
 */
public final class EnchanterJob extends AbstractJobHandler {

    public EnchanterJob() {
        super(JobType.ENCHANTER, Map.of(
                "ENCHANT_ITEM", 2.5,
                "USE_ENCHANTING_TABLE", 1.0
        ));
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return super.appliesTo(actionKey)
                || (actionKey != null && actionKey.startsWith("ANVIL_"));
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey != null && actionKey.startsWith("ANVIL_")) {
            return 1.8;
        }
        return super.getRewardMultiplier(actionKey);
    }
}
