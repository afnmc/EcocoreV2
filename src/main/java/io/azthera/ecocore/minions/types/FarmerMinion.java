package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

import java.util.Map;

/**
 * Plants, harvests, and auto-replants crops within its work radius -
 * this single type absorbs what used to be three separate types
 * (Farmer, Planter, Harvester). Never harvests an immature crop:
 * {@code MinionAiController.handleFarmCycle} checks each crop's
 * actual growth stage before touching it. Uses dual storage: Storage
 * A (the first few slots) holds seeds/saplings for replanting,
 * Storage B (the rest) holds harvested produce - see
 * {@code MinionAiController.ZONE_A_SLOTS}.
 *
 * <p>The material map here declares every crop/produce block this
 * minion recognizes and what harvesting it yields; it's read by
 * {@code MinionAiController.handleFarmCycle} for both the maturity
 * scan and the harvest result, not by the generic
 * {@link MinionProcessingType#BLOCK_BREAK} path (this type uses
 * {@link MinionProcessingType#FARM_CYCLE} instead, which is fully
 * custom logic since it also has to plant and replant).
 */
public final class FarmerMinion extends AbstractMinionHandler {

    public FarmerMinion() {
        super(MinionType.FARMER, MinionProcessingType.FARM_CYCLE, 2,
                Map.ofEntries(
                        Map.entry(Material.WHEAT, Material.WHEAT),
                        Map.entry(Material.CARROTS, Material.CARROT),
                        Map.entry(Material.POTATOES, Material.POTATO),
                        Map.entry(Material.BEETROOTS, Material.BEETROOT),
                        Map.entry(Material.PUMPKIN, Material.PUMPKIN),
                        Map.entry(Material.MELON, Material.MELON_SLICE),
                        Map.entry(Material.NETHER_WART, Material.NETHER_WART)
                ),
                noEntities(), null, null, noCatches());
    }
}
