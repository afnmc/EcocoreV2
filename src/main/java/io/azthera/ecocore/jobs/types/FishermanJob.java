package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards fishing rod catches, including treasure/junk outcomes.
 */
public final class FishermanJob extends AbstractJobHandler {

    public FishermanJob() {
        super(JobType.FISHERMAN, Map.ofEntries(
                Map.entry("CATCH_COD", 1.0),
                Map.entry("CATCH_SALMON", 1.1),
                Map.entry("CATCH_PUFFERFISH", 1.3),
                Map.entry("CATCH_TROPICAL_FISH", 1.4),
                Map.entry("CATCH_TREASURE", 3.0),
                Map.entry("CATCH_JUNK", 0.3)
        ));
    }
}