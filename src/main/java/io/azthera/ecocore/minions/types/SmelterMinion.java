package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Converts raw ore items already present in its own storage into
 * their smelted form, without needing an external furnace or fuel
 * beyond the minion's own fuel/energy system.
 */
public final class SmelterMinion extends AbstractMinionHandler {

    public SmelterMinion() {
        super(MinionType.SMELTER, MinionProcessingType.INTERNAL_SMELT, 3,
                Map.ofEntries(
                        Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
                        Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
                        Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
                        Map.entry(Material.COBBLESTONE, Material.STONE),
                        Map.entry(Material.SAND, Material.GLASS)
                ),
                noEntities(), null, null, noCatches());
    }
}