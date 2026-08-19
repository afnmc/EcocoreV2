package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Mines a broad range of terrain and ore blocks over a wide work
 * radius. {@code MinionAiController} applies an extra radius
 * multiplier specifically for this type to reflect its "quarry"
 * (wide-area strip mining) role.
 */
public final class QuarryMinion extends AbstractMinionHandler {

    /** Radius multiplier applied on top of the minion's normal upgraded radius. */
    public static final double RADIUS_MULTIPLIER = 1.5;

    public QuarryMinion() {
        super(MinionType.QUARRY, MinionProcessingType.BLOCK_BREAK, 2,
                Map.ofEntries(
                        Map.entry(Material.STONE, Material.COBBLESTONE),
                        Map.entry(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE),
                        Map.entry(Material.DIRT, Material.DIRT),
                        Map.entry(Material.GRAVEL, Material.GRAVEL),
                        Map.entry(Material.COAL_ORE, Material.COAL),
                        Map.entry(Material.IRON_ORE, Material.RAW_IRON)
                ),
                noEntities(), null, null, noCatches());
    }
}