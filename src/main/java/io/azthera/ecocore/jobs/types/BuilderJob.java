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
}