package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards successfully breeding animals.
 */
public final class BreederJob extends AbstractJobHandler {

    public BreederJob() {
        super(JobType.BREEDER, Map.ofEntries(
                Map.entry("BREED_COW", 1.0),
                Map.entry("BREED_PIG", 1.0),
                Map.entry("BREED_SHEEP", 1.0),
                Map.entry("BREED_CHICKEN", 0.8),
                Map.entry("BREED_HORSE", 1.8),
                Map.entry("BREED_WOLF", 1.5),
                Map.entry("BREED_CAT", 1.2),
                Map.entry("BREED_RABBIT", 1.0),
                Map.entry("BREED_VILLAGER", 2.0),
                Map.entry("BREED_FOX", 1.5),
                Map.entry("BREED_LLAMA", 1.5),
                Map.entry("BREED_PANDA", 2.0),
                Map.entry("BREED_BEE", 1.2),
                Map.entry("BREED_TURTLE", 1.8),
                Map.entry("BREED_GOAT", 1.5),
                Map.entry("BREED_FROG", 1.5),
                Map.entry("BREED_CAMEL", 2.0),
                Map.entry("BREED_ARMADILLO", 1.5),
                Map.entry("BREED_SNIFFER", 3.0)
        ));
    }

    @Override
    public boolean appliesTo(String actionKey) {
        if (actionKey == null) return false;
        return super.appliesTo(actionKey) || actionKey.startsWith("BREED_");
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey == null) return 0.0;
        double base = super.getRewardMultiplier(actionKey);
        if (base > 0) return base;
        if (actionKey.startsWith("BREED_")) return 1.0;
        return 0.0;
    }
}