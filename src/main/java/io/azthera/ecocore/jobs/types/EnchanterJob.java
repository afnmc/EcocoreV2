package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards enchanting and anvil-combining items.
 */
public final class EnchanterJob extends AbstractJobHandler {

    public EnchanterJob() {
        super(JobType.ENCHANTER, Map.ofEntries(
                Map.entry("ENCHANT_ITEM", 2.5),
                Map.entry("USE_ENCHANTING_TABLE", 1.0),
                Map.entry("COMBINE_ANVIL", 1.8)
        ));
    }
}