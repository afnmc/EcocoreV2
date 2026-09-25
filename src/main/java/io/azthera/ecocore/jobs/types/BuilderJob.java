package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards placing construction blocks.
 */
public final class BuilderJob extends AbstractJobHandler {

    public BuilderJob() {
        super(JobType.BUILDER, Map.ofEntries(
                Map.entry("PLACE_BLOCK", 0.4),
                Map.entry("PLACE_STAIRS", 0.5),
                Map.entry("PLACE_SLAB", 0.4),
                Map.entry("PLACE_WALL", 0.5),
                Map.entry("PLACE_FENCE", 0.5)
        ));
    }

    @Override
    public boolean appliesTo(String actionKey) {
        if (actionKey == null) return false;
        return super.appliesTo(actionKey) || actionKey.startsWith("PLACE_");
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey == null) return 0.0;
        if (actionKey.equals("PLACE_STAIRS") || actionKey.endsWith("_STAIRS")) return 0.5;
        if (actionKey.equals("PLACE_WALL") || actionKey.endsWith("_WALL")) return 0.5;
        if (actionKey.equals("PLACE_FENCE") || actionKey.endsWith("_FENCE") || actionKey.endsWith("_FENCE_GATE")) return 0.5;
        if (actionKey.equals("PLACE_SLAB") || actionKey.endsWith("_SLAB")) return 0.4;
        if (actionKey.startsWith("PLACE_")) return 0.4;
        return super.getRewardMultiplier(actionKey);
    }
}