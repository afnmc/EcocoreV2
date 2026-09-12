package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards brewing potions of increasing complexity.
 */
public final class AlchemyJob extends AbstractJobHandler {

    public AlchemyJob() {
        super(JobType.ALCHEMY, Map.ofEntries(
                Map.entry("BREW_POTION", 1.5),
                Map.entry("BREW_SPLASH_POTION", 2.0),
                Map.entry("BREW_LINGERING_POTION", 2.5)
        ));
    }
}