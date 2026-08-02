package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards completing quests, weighted by quest tier.
 */
public final class QuestHunterJob extends AbstractJobHandler {

    public QuestHunterJob() {
        super(JobType.QUEST_HUNTER, Map.ofEntries(
                Map.entry("COMPLETE_QUEST", 2.0),
                Map.entry("COMPLETE_DAILY_QUEST", 2.5),
                Map.entry("COMPLETE_BOSS_QUEST", 5.0)
        ));
    }
}