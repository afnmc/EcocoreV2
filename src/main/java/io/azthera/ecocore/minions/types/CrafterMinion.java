package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Converts simple raw materials already present in its own storage
 * into common crafted goods, representing a simplified always-on
 * crafting table.
 */
public final class CrafterMinion extends AbstractMinionHandler {

    public CrafterMinion() {
        super(MinionType.CRAFTER, MinionProcessingType.INTERNAL_CRAFT, 2,
                Map.ofEntries(
                        Map.entry(Material.OAK_PLANKS, Material.STICK),
                        Map.entry(Material.WHEAT, Material.BREAD),
                        Map.entry(Material.IRON_INGOT, Material.IRON_NUGGET),
                        Map.entry(Material.COBBLESTONE, Material.STONE_BRICKS)
                ),
                noEntities(), null, null, noCatches());
    }
}