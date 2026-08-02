package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards smelting ores, repairing gear, and anvil work.
 */
public final class BlacksmithJob extends AbstractJobHandler {

    public BlacksmithJob() {
        super(JobType.BLACKSMITH, Map.ofEntries(
                Map.entry("SMELT_ORE", 1.2),
                Map.entry("REPAIR_ITEM", 1.5),
                Map.entry("CRAFT_ANVIL_RESULT", 1.8)
        ));
    }
}