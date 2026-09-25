package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards breaking logs of every wood type.
 */
public final class WoodcutterJob extends AbstractJobHandler {

    public WoodcutterJob() {
        super(JobType.WOODCUTTER, Map.ofEntries(
                Map.entry("BREAK_OAK_LOG", 1.0),
                Map.entry("BREAK_BIRCH_LOG", 1.0),
                Map.entry("BREAK_SPRUCE_LOG", 1.0),
                Map.entry("BREAK_JUNGLE_LOG", 1.1),
                Map.entry("BREAK_ACACIA_LOG", 1.1),
                Map.entry("BREAK_DARK_OAK_LOG", 1.2),
                Map.entry("BREAK_MANGROVE_LOG", 1.2),
                Map.entry("BREAK_CHERRY_LOG", 1.3),
                Map.entry("BREAK_PALE_OAK_LOG", 1.3),
                Map.entry("BREAK_CRIMSON_STEM", 1.4),
                Map.entry("BREAK_WARPED_STEM", 1.4),
                Map.entry("BREAK_STRIPPED_OAK_LOG", 1.0),
                Map.entry("BREAK_STRIPPED_BIRCH_LOG", 1.0),
                Map.entry("BREAK_STRIPPED_SPRUCE_LOG", 1.0),
                Map.entry("BREAK_STRIPPED_JUNGLE_LOG", 1.1),
                Map.entry("BREAK_STRIPPED_ACACIA_LOG", 1.1),
                Map.entry("BREAK_STRIPPED_DARK_OAK_LOG", 1.2),
                Map.entry("BREAK_STRIPPED_MANGROVE_LOG", 1.2),
                Map.entry("BREAK_STRIPPED_CHERRY_LOG", 1.3),
                Map.entry("BREAK_STRIPPED_PALE_OAK_LOG", 1.3),
                Map.entry("BREAK_STRIPPED_CRIMSON_STEM", 1.4),
                Map.entry("BREAK_STRIPPED_WARPED_STEM", 1.4),
                Map.entry("BREAK_OAK_WOOD", 1.0),
                Map.entry("BREAK_BIRCH_WOOD", 1.0),
                Map.entry("BREAK_SPRUCE_WOOD", 1.0),
                Map.entry("BREAK_JUNGLE_WOOD", 1.1),
                Map.entry("BREAK_ACACIA_WOOD", 1.1),
                Map.entry("BREAK_DARK_OAK_WOOD", 1.2),
                Map.entry("BREAK_MANGROVE_WOOD", 1.2),
                Map.entry("BREAK_CHERRY_WOOD", 1.3),
                Map.entry("BREAK_PALE_OAK_WOOD", 1.3),
                Map.entry("BREAK_CRIMSON_HYPHAE", 1.4),
                Map.entry("BREAK_WARPED_HYPHAE", 1.4)
        ));
    }
}