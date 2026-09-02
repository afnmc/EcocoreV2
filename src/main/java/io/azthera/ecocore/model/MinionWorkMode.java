package io.azthera.ecocore.model;

public enum MinionWorkMode {

    FACING_ONLY,
    ARENA_ONLY,
    BOTH,
    NONE;

    public static MinionWorkMode defaultFor(MinionType type) {
        return switch (type) {
            case MINER, QUARRY, LUMBERJACK, FARMER, COLLECTOR -> BOTH;
            case FISHERMAN -> ARENA_ONLY;
            case SMELTER, CHEST, SELL, STORAGE -> NONE;
            case MOB_KILLER -> ARENA_ONLY;
        };
    }

    public static MinionWorkMode fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        for (MinionWorkMode mode : values()) {
            if (mode.name().equalsIgnoreCase(key)) {
                return mode;
            }
        }
        return null;
    }
}