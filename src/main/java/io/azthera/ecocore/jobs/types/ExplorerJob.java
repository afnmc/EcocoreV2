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
                Map.entry("TRAVEL_DISTANCE", 0.1)
        ));
    }
}