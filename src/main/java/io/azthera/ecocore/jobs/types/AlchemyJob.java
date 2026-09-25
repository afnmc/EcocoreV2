package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards brewing potions of increasing complexity.
 */
public final class AlchemyJob extends AbstractJobHandler {

    public AlchemyJob() {
        super(JobType.ALCHEMY, Map.ofEntries(
                Map.entry("BREW_POTION", 1.5),
                Map.entry("BREW_SPLASH_POTION", 2.0),
                Map.entry("BREW_LINGERING_POTION", 2.5)
        ));
    }

    @Override
    public boolean appliesTo(String actionKey) {
        if (actionKey == null) return false;
        return super.appliesTo(actionKey) || actionKey.startsWith("BREW_");
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey != null && actionKey.startsWith("BREW_LINGERING")) return 2.5;
        if (actionKey != null && actionKey.startsWith("BREW_SPLASH")) return 2.0;
        if (actionKey != null && actionKey.startsWith("BREW_")) return 1.5;
        return super.getRewardMultiplier(actionKey);
    }
}