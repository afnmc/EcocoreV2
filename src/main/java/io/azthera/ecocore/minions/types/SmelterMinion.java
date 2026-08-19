package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Converts raw ore items (and simple crafting-style conversions -
 * this handler absorbed the former standalone Crafter minion's
 * recipes) already present in its own storage into their processed
 * form, without needing an external furnace/crafting table.
 */
public final class SmelterMinion extends AbstractMinionHandler {

    public SmelterMinion() {
        super(MinionType.SMELTER, MinionProcessingType.INTERNAL_SMELT, 3,
                Map.ofEntries(
                        Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
                        Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
                        Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
                        Map.entry(Material.COBBLESTONE, Material.STONE),
                        Map.entry(Material.SAND, Material.GLASS),
                        // Merged from the former Crafter minion:
                        Map.entry(Material.OAK_PLANKS, Material.STICK),
                        Map.entry(Material.WHEAT, Material.BREAD),
                        Map.entry(Material.IRON_INGOT, Material.IRON_NUGGET)
                ),
                noEntities(), null, null, noCatches());
    }
}
