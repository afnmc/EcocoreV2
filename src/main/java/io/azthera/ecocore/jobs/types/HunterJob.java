package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards defeating hostile mobs, weighted by relative difficulty.
 */
public final class HunterJob extends AbstractJobHandler {

    public HunterJob() {
        super(JobType.HUNTER, Map.ofEntries(
                Map.entry("KILL_ZOMBIE", 1.0),
                Map.entry("KILL_SKELETON", 1.1),
                Map.entry("KILL_SPIDER", 1.0),
                Map.entry("KILL_CREEPER", 1.3),
                Map.entry("KILL_ENDERMAN", 2.5),
                Map.entry("KILL_WITCH", 2.2),
                Map.entry("KILL_BLAZE", 2.8),
                Map.entry("KILL_WITHER_SKELETON", 3.2)
        ));
    }
}