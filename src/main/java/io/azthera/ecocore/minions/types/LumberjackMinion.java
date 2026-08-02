package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Breaks logs of every common wood type within its work radius.
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
                        Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_LOG)
                ),
                noEntities(), null, null, noCatches());
    }
}