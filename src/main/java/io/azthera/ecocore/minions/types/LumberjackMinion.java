package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Breaks logs of every common wood type within its work radius, and
 * (via {@code MinionAiController.handleBlockBreak}) auto-replants a
 * matching sapling from its Storage A reserve when it fells the base
 * log of a trunk (the log with non-log ground directly beneath it).
 */
public final class LumberjackMinion extends AbstractMinionHandler {

    public LumberjackMinion() {
        super(MinionType.LUMBERJACK, MinionProcessingType.BLOCK_BREAK, 2,
                Map.ofEntries(
                        Map.entry(Material.OAK_LOG, Material.OAK_LOG),
                        Map.entry(Material.BIRCH_LOG, Material.BIRCH_LOG),
                        Map.entry(Material.SPRUCE_LOG, Material.SPRUCE_LOG),
                        Map.entry(Material.JUNGLE_LOG, Material.JUNGLE_LOG),
                        Map.entry(Material.ACACIA_LOG, Material.ACACIA_LOG),
                        Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_LOG),
                        Map.entry(Material.MANGROVE_LOG, Material.MANGROVE_LOG),
                        Map.entry(Material.CHERRY_LOG, Material.CHERRY_LOG),
                        Map.entry(Material.CRIMSON_STEM, Material.CRIMSON_STEM),
                        Map.entry(Material.WARPED_STEM, Material.WARPED_STEM)
                ),
                noEntities(), null, null, noCatches());
    }
}
