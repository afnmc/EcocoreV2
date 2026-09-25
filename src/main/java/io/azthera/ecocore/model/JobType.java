package io.azthera.ecocore.model;

/**
 * All job types supported by EcoCore's built-in Jobs system.
 * The enum name (lowercased) is used as the storage key in
 * {@code jobs.yml} and in the {@code jobs_data} database table.
 */
public enum JobType {
    MINER,
    WOODCUTTER,
    FARMER,
    HUNTER,
    FISHERMAN,
    EXCAVATOR,
    BUILDER,
    CRAFTER,
    EXPLORER,
    BREEDER,
    COOK,
    BLACKSMITH,
    ENCHANTER,
    ALCHEMY,
    MERCHANT,
    QUEST_HUNTER;

    /**
     * Returns the lowercase config key used in jobs.yml (e.g. "quest_hunter").
     *
     * @return the config key for this job type
     */
    public String configKey() {
        return name().toLowerCase();
    }

    /**
     * Resolves a job type from its config key, case-insensitively.
     *
     * @param key the config key (e.g. "miner", "quest_hunter")
     * @return the matching JobType, or {@code null} if none matches
     */
    public static JobType fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        for (JobType type : values()) {
            if (type.configKey().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}