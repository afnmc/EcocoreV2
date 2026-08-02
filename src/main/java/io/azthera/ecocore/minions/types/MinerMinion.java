package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Breaks stone and ore blocks within its work radius.
 */
public final class MinerMinion extends AbstractMinionHandler {

    public MinerMinion() {
        super(MinionType.MINER, MinionProcessingType.BLOCK_BREAK, 2,
                Map.ofEntries(
                        Map.entry(Material.STONE, Material.COBBLESTONE),
                        Map.entry(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE),
                        Map.entry(Material.COAL_ORE, Material.COAL),
                        Map.entry(Material.IRON_ORE, Material.RAW_IRON),
                        Map.entry(Material.GOLD_ORE, Material.RAW_GOLD),
                        Map.entry(Material.DIAMOND_ORE, Material.DIAMOND),
                        Map.entry(Material.EMERALD_ORE, Material.EMERALD),
                        Map.entry(Material.REDSTONE_ORE, Material.REDSTONE),
                        Map.entry(Material.LAPIS_ORE, Material.LAPIS_LAZULI)
                ),
                noEntities(), null, null, noCatches());
    }
}