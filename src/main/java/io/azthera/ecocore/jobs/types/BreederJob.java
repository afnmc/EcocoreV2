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
                Map.entry("BREED_WOLF", 1.5)
        ));
    }
}