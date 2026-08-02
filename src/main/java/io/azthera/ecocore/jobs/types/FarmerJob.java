package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards harvesting mature crops.
 */
public final class FarmerJob extends AbstractJobHandler {

    public FarmerJob() {
        super(JobType.FARMER, Map.ofEntries(
                Map.entry("HARVEST_WHEAT", 1.0),
                Map.entry("HARVEST_CARROT", 0.9),
                Map.entry("HARVEST_POTATO", 0.9),
                Map.entry("HARVEST_BEETROOT", 1.0),
                Map.entry("HARVEST_PUMPKIN", 1.5),
                Map.entry("HARVEST_MELON", 1.4),
                Map.entry("HARVEST_SUGAR_CANE", 0.7),
                Map.entry("HARVEST_NETHER_WART", 1.6)
        ));
    }
}