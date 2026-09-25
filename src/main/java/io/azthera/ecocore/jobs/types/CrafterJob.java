package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards crafting-table results. The concrete material is carried in the
 * action key (for example CRAFT_STICK), allowing target-specific missions.
 */
public final class CrafterJob extends AbstractJobHandler {

    public CrafterJob() {
        super(JobType.CRAFTER, Map.of());
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return actionKey != null && actionKey.startsWith("CRAFT_")
                && actionKey.length() > "CRAFT_".length();
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (!appliesTo(actionKey)) {
            return 0.0;
        }
        String material = actionKey.substring("CRAFT_".length());
        if (material.endsWith("_SWORD") || material.endsWith("_PICKAXE")
                || material.endsWith("_AXE") || material.endsWith("_SHOVEL")
                || material.endsWith("_HOE")) {
            return 1.0;
        }
        if (material.endsWith("_HELMET") || material.endsWith("_CHESTPLATE")
                || material.endsWith("_LEGGINGS") || material.endsWith("_BOOTS")) {
            return 1.3;
        }
        return 0.5;
    }
}
