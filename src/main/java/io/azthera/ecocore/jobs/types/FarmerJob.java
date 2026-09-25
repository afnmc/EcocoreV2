package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards harvesting mature crops.
 */
public final class FarmerJob extends AbstractJobHandler {

    public FarmerJob() {
        super(JobType.FARMER, Map.ofEntries(
                Map.entry("HARVEST_WHEAT", 1.0),
                Map.entry("BREAK_WHEAT", 1.0),
                Map.entry("HARVEST_CARROT", 0.9),
                Map.entry("HARVEST_CARROTS", 0.9),
                Map.entry("BREAK_CARROTS", 0.9),
                Map.entry("HARVEST_POTATO", 0.9),
                Map.entry("HARVEST_POTATOES", 0.9),
                Map.entry("BREAK_POTATOES", 0.9),
                Map.entry("HARVEST_BEETROOT", 1.0),
                Map.entry("HARVEST_BEETROOTS", 1.0),
                Map.entry("BREAK_BEETROOTS", 1.0),
                Map.entry("HARVEST_PUMPKIN", 1.5),
                Map.entry("BREAK_PUMPKIN", 1.5),
                Map.entry("HARVEST_MELON", 1.4),
                Map.entry("BREAK_MELON", 1.4),
                Map.entry("HARVEST_SUGAR_CANE", 0.7),
                Map.entry("BREAK_SUGAR_CANE", 0.7),
                Map.entry("HARVEST_NETHER_WART", 1.6),
                Map.entry("BREAK_NETHER_WART", 1.6),
                Map.entry("HARVEST_COCOA", 1.2),
                Map.entry("BREAK_COCOA", 1.2),
                Map.entry("HARVEST_CACTUS", 0.8),
                Map.entry("BREAK_CACTUS", 0.8),
                Map.entry("HARVEST_BAMBOO", 0.5),
                Map.entry("BREAK_BAMBOO", 0.5),
                Map.entry("HARVEST_SWEET_BERRIES", 0.8),
                Map.entry("HARVEST_SWEET_BERRY_BUSH", 0.8),
                Map.entry("BREAK_SWEET_BERRY_BUSH", 0.8),
                Map.entry("HARVEST_CAVE_VINES", 0.8),
                Map.entry("BREAK_CAVE_VINES", 0.8)
        ));
    }
}