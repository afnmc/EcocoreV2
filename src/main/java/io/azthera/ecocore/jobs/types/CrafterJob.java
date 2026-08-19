package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards crafting table results, weighted by item complexity.
 */
public final class CrafterJob extends AbstractJobHandler {

    public CrafterJob() {
        super(JobType.CRAFTER, Map.ofEntries(
                Map.entry("CRAFT_ITEM", 0.5),
                Map.entry("CRAFT_TOOL", 1.0),
                Map.entry("CRAFT_ARMOR", 1.3),
                Map.entry("CRAFT_BLOCK", 0.4)
        ));
    }
}