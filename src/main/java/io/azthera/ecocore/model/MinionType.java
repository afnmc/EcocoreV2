// FILE: src/main/java/io/azthera/ecocore/model/MinionType.java
package io.azthera.ecocore.model;

public enum MinionType {

    MINER,
    LUMBERJACK,
    FARMER,
    FISHERMAN,
    COLLECTOR,
    MOB_KILLER,
    SMELTER,
    SELL,
    QUARRY,
    CHEST;

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