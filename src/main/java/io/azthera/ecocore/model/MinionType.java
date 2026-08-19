package io.azthera.ecocore.model;

public enum MinionType {
    MINER,
    LUMBERJACK,
    FARMER,
    FISHING,
    COLLECTOR,
    MOB_KILLER,
    ANIMAL_FARMER,
    SMELTER,
    SELLER,
    BREEDER,
    QUARRY,
    MINION_CHEST;

    public String configKey() {
        return name().toLowerCase();
    }

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
