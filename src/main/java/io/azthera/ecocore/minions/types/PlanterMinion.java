package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

/**
 * Consumes wheat seeds from its own storage on a fixed cycle and
 * produces harvested wheat, modeling an automated planting-and-growing
 * loop without needing to track individual farmland block state.
 */
public final class PlanterMinion extends AbstractMinionHandler {

    public PlanterMinion() {
        super(MinionType.PLANTER, MinionProcessingType.BLOCK_PLACE, 2,
                noMaterials(), noEntities(), Material.WHEAT_SEEDS, Material.WHEAT, noCatches());
    }
}