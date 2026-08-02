package io.azthera.ecocore.model;

/**
 * All minion types supported by EcoCore's built-in Minions system.
 */
public enum MinionType {
    MINER,
    LUMBERJACK,
    FARMER,
    FISHING,
    COLLECTOR,
    MOB_KILLER,
    ANIMAL_FARMER,
    SMELTER,
    CRAFTER,
    STORAGE,
    SELLER,
    HARVESTER,
    PLANTER,
    BREEDER,
    QUARRY;

    /**
     * Returns the lowercase config key used in minions.yml (e.g. "mob_killer").
     *
     * @return the config key for this minion type
     */
    public String configKey() {
        return name().toLowerCase();
    }

    /**
     * Resolves a minion type from its config key, case-insensitively.
     *
     * @param key the config key (e.g. "miner", "mob_killer")
     * @return the matching MinionType, or {@code null} if none matches
     */
    public static MinionType fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        for (MinionType type : values()) {
            if (type.configKey().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}