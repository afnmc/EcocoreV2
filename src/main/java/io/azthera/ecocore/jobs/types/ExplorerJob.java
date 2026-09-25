package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards discovering new biomes, structures, and traveling distance.
 */
public final class ExplorerJob extends AbstractJobHandler {

    public ExplorerJob() {
        super(JobType.EXPLORER, Map.ofEntries(
                Map.entry("DISCOVER_BIOME", 2.0),
                Map.entry("ENTER_STRUCTURE", 2.5),
                Map.entry("ENTER_VILLAGE", 1.5),
                Map.entry("ENTER_CAVE", 1.0),
                Map.entry("TRAVEL_DISTANCE", 0.1),
                Map.entry("DELIVER_ITEM", 1.5),
                Map.entry("COLLECT_ITEM", 0.8)
        ));
    }

    @Override
    public boolean appliesTo(String actionKey) {
        if (actionKey == null) return false;
        return super.appliesTo(actionKey)
                || actionKey.startsWith("DISCOVER_BIOME_")
                || actionKey.startsWith("DELIVER_ITEM_")
                || actionKey.startsWith("COLLECT_ITEM_");
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey == null) return 0.0;
        if (actionKey.startsWith("DISCOVER_BIOME")) return 2.0;
        if (actionKey.startsWith("DELIVER_ITEM")) return 1.5;
        if (actionKey.startsWith("COLLECT_ITEM")) return 0.8;
        return super.getRewardMultiplier(actionKey);
    }
}