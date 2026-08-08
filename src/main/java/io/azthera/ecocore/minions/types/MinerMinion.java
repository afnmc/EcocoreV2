package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Breaks stone and ore blocks within its work radius, including
 * deepslate variants, tuff, copper, and nether ores.
 */
public final class MinerMinion extends AbstractMinionHandler {

    public MinerMinion() {
        super(MinionType.MINER, MinionProcessingType.BLOCK_BREAK, 2,
                Map.ofEntries(
                        Map.entry(Material.STONE, Material.COBBLESTONE),
                        Map.entry(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE),
                        Map.entry(Material.TUFF, Material.TUFF),
                        Map.entry(Material.CALCITE, Material.CALCITE),
                        Map.entry(Material.COAL_ORE, Material.COAL),
                        Map.entry(Material.DEEPSLATE_COAL_ORE, Material.COAL),
                        Map.entry(Material.IRON_ORE, Material.RAW_IRON),
                        Map.entry(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON),
                        Map.entry(Material.GOLD_ORE, Material.RAW_GOLD),
                        Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD),
                        Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET),
                        Map.entry(Material.DIAMOND_ORE, Material.DIAMOND),
                        Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND),
                        Map.entry(Material.EMERALD_ORE, Material.EMERALD),
                        Map.entry(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD),
                        Map.entry(Material.REDSTONE_ORE, Material.REDSTONE),
                        Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE),
                        Map.entry(Material.LAPIS_ORE, Material.LAPIS_LAZULI),
                        Map.entry(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI),
                        Map.entry(Material.COPPER_ORE, Material.RAW_COPPER),
                        Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER),
                        Map.entry(Material.NETHER_QUARTZ_ORE, Material.QUARTZ),
                        Map.entry(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS)
                ),
                noEntities(), null, null, noCatches());
    }
}
