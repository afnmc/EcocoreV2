package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Harvests a wider range of crop and produce blocks than
 * {@link FarmerMinion}, including pumpkins and melons.
 */
public final class HarvesterMinion extends AbstractMinionHandler {

    public HarvesterMinion() {
        super(MinionType.HARVESTER, MinionProcessingType.BLOCK_BREAK, 2,
                Map.ofEntries(
                        Map.entry(Material.WHEAT, Material.WHEAT),
                        Map.entry(Material.CARROTS, Material.CARROT),
                        Map.entry(Material.POTATOES, Material.POTATO),
                        Map.entry(Material.BEETROOTS, Material.BEETROOT),
                        Map.entry(Material.PUMPKIN, Material.PUMPKIN),
                        Map.entry(Material.MELON, Material.MELON_SLICE)
                ),
                noEntities(), null, null, noCatches());
    }
}