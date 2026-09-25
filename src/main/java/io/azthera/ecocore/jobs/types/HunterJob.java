package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards defeating any entity that produces a KILL_<ENTITY_TYPE> action.
 * Known mobs keep their configured difficulty multiplier; unlisted entities
 * receive a safe default multiplier.
 */
public final class HunterJob extends AbstractJobHandler {

    private static final Map<String, Double> MULTIPLIERS = Map.ofEntries(
            Map.entry("ZOMBIE", 1.0),
            Map.entry("SKELETON", 1.1),
            Map.entry("SPIDER", 1.0),
            Map.entry("CREEPER", 1.3),
            Map.entry("ENDERMAN", 2.5),
            Map.entry("WITCH", 2.2),
            Map.entry("BLAZE", 2.8),
            Map.entry("WITHER_SKELETON", 3.2)
    );

    public HunterJob() {
        super(JobType.HUNTER, Map.of());
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return actionKey != null && actionKey.startsWith("KILL_")
                && actionKey.length() > "KILL_".length();
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (!appliesTo(actionKey)) {
            return 0.0;
        }
        return MULTIPLIERS.getOrDefault(actionKey.substring("KILL_".length()), 1.0);
    }
}
