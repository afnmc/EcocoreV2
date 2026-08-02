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
                Map.entry("BREAK_CHERRY_LOG", 1.3)
        ));
    }
}