package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards breaking stone, ores, and deepslate variants.
 */
public final class MinerJob extends AbstractJobHandler {

    public MinerJob() {
        super(JobType.MINER, Map.ofEntries(
                Map.entry("BREAK_STONE", 0.3),
                Map.entry("BREAK_COBBLESTONE", 0.3),
                Map.entry("BREAK_DEEPSLATE", 0.4),
                Map.entry("BREAK_COAL_ORE", 1.0),
                Map.entry("BREAK_IRON_ORE", 1.5),
                Map.entry("BREAK_GOLD_ORE", 2.0),
                Map.entry("BREAK_REDSTONE_ORE", 1.2),
                Map.entry("BREAK_LAPIS_ORE", 1.2),
                Map.entry("BREAK_EMERALD_ORE", 3.5),
                Map.entry("BREAK_DIAMOND_ORE", 4.0),
                Map.entry("BREAK_ANCIENT_DEBRIS", 6.0)
        ));
    }
}