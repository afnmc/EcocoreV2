package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards quest-hunter actions, including generic mob-kill missions.
 */
public final class QuestHunterJob extends AbstractJobHandler {

    public QuestHunterJob() {
        super(JobType.QUEST_HUNTER, Map.ofEntries(
                Map.entry("COMPLETE_QUEST", 2.0),
                Map.entry("COMPLETE_DAILY_QUEST", 2.5),
                Map.entry("COMPLETE_BOSS_QUEST", 5.0)
        ));
    }

    /**
     * Quest Hunter has a KILL_MOB mission whose target is empty, meaning
     * any mob/entity. EntityDeathListener emits concrete keys such as
     * KILL_ZOMBIE, KILL_COW, KILL_SHEEP, etc., so this job must accept
     * the KILL_ prefix instead of enumerating every entity type.
     */
    @Override
    public boolean appliesTo(String actionKey) {
        return super.appliesTo(actionKey)
                || (actionKey != null && actionKey.toUpperCase().startsWith("KILL_"));
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if (actionKey != null && actionKey.toUpperCase().startsWith("KILL_")) {
            return 1.0;
        }
        return super.getRewardMultiplier(actionKey);
    }
}
