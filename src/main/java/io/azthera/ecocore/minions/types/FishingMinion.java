package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.List;

/**
 * Casts periodically near water and produces a random catch from a
 * simplified table (fish plus a rare treasure item).
 */
public final class FishingMinion extends AbstractMinionHandler {

    public FishingMinion() {
        super(MinionType.FISHING, MinionProcessingType.FISHING, 3,
                noMaterials(), noEntities(), null, null,
                List.of(Material.COD, Material.SALMON, Material.PUFFERFISH,
                        Material.TROPICAL_FISH, Material.NAUTILUS_SHELL));
    }
}