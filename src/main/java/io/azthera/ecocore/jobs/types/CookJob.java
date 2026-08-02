package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards smelting/cooking/baking food items.
 */
public final class CookJob extends AbstractJobHandler {

    public CookJob() {
        super(JobType.COOK, Map.ofEntries(
                Map.entry("SMELT_FOOD", 0.7),
                Map.entry("COOK_FOOD", 0.7),
                Map.entry("BAKE_FOOD", 0.9)
        ));
    }
}