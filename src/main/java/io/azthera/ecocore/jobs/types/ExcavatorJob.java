package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards breaking loose/soft terrain blocks.
 */
public final class ExcavatorJob extends AbstractJobHandler {

    public ExcavatorJob() {
        super(JobType.EXCAVATOR, Map.ofEntries(
                Map.entry("BREAK_DIRT", 0.3),
                Map.entry("BREAK_SAND", 0.4),
                Map.entry("BREAK_GRAVEL", 0.5),
                Map.entry("BREAK_CLAY", 0.8),
                Map.entry("BREAK_SOUL_SAND", 0.9),
                Map.entry("BREAK_SNOW", 0.2)
        ));
    }
}