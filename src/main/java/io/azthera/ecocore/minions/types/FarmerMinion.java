package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

public final class FarmerMinion extends AbstractMinionHandler {
    public FarmerMinion() {
        super(MinionType.FARMER, MinionProcessingType.FARM_CYCLE, noEntities(),
                Material.WHEAT_SEEDS, Material.WHEAT);
    }
}
