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
                Map.entry("BREAK_ANDESITE", 0.3),
                Map.entry("BREAK_DIORITE", 0.3),
                Map.entry("BREAK_GRANITE", 0.3),
                Map.entry("BREAK_TUFF", 0.3),
                Map.entry("BREAK_CALCITE", 0.4),
                Map.entry("BREAK_DRIPSTONE_BLOCK", 0.4),
                Map.entry("BREAK_BASALT", 0.4),
                Map.entry("BREAK_SMOOTH_BASALT", 0.4),
                Map.entry("BREAK_BLACKSTONE", 0.4),
                Map.entry("BREAK_DEEPSLATE", 0.4),
                Map.entry("BREAK_COBBLED_DEEPSLATE", 0.4),
                Map.entry("BREAK_NETHERRACK", 0.2),
                Map.entry("BREAK_END_STONE", 0.5),
                Map.entry("BREAK_OBSIDIAN", 2.0),
                Map.entry("BREAK_CRYING_OBSIDIAN", 2.5),
                Map.entry("BREAK_COAL_ORE", 1.0),
                Map.entry("BREAK_DEEPSLATE_COAL_ORE", 1.2),
                Map.entry("BREAK_COPPER_ORE", 1.0),
                Map.entry("BREAK_DEEPSLATE_COPPER_ORE", 1.2),
                Map.entry("BREAK_IRON_ORE", 1.5),
                Map.entry("BREAK_DEEPSLATE_IRON_ORE", 1.8),
                Map.entry("BREAK_GOLD_ORE", 2.0),
                Map.entry("BREAK_DEEPSLATE_GOLD_ORE", 2.3),
                Map.entry("BREAK_NETHER_GOLD_ORE", 1.5),
                Map.entry("BREAK_REDSTONE_ORE", 1.2),
                Map.entry("BREAK_DEEPSLATE_REDSTONE_ORE", 1.4),
                Map.entry("BREAK_LAPIS_ORE", 1.2),
                Map.entry("BREAK_DEEPSLATE_LAPIS_ORE", 1.4),
                Map.entry("BREAK_NETHER_QUARTZ_ORE", 1.3),
                Map.entry("BREAK_EMERALD_ORE", 3.5),
                Map.entry("BREAK_DEEPSLATE_EMERALD_ORE", 4.0),
                Map.entry("BREAK_DIAMOND_ORE", 4.0),
                Map.entry("BREAK_DEEPSLATE_DIAMOND_ORE", 4.5),
                Map.entry("BREAK_ANCIENT_DEBRIS", 6.0)
        ));
    }
}