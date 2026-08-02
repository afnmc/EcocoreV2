package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Harvests mature crop blocks within its work radius. Replanting is
 * not separately simulated - the crop block is treated as
 * immediately available again next cycle, modeling an idealized farm.
 */
public final class FarmerMinion extends AbstractMinionHandler {

    public FarmerMinion() {
        super(MinionType.FARMER, MinionProcessingType.BLOCK_BREAK, 2,
                Map.ofEntries(
                        Map.entry(Material.WHEAT, Material.WHEAT),
                        Map.entry(Material.CARROTS, Material.CARROT),
                        Map.entry(Material.POTATOES, Material.POTATO),
                        Map.entry(Material.BEETROOTS, Material.BEETROOT)
                ),
                noEntities(), null, null, noCatches());
    }
}