// FILE: src/main/java/io/azthera/ecocore/model/MinionWorkMode.java
package io.azthera.ecocore.model;

/**
 * Determines which area a minion operates in when looking for work.
 *
 * FACING_ONLY - the minion only works in the direction it is
 * facing (cardinal-locked at placement, like a piston/dispenser).
 * ARENA_ONLY - the minion works in a full 360-degree radius around
 * itself, ignoring facing. BOTH - the minion may use either mode,
 * togglable by the player (defaults to arena). NONE - the minion
 * does not operate on the world at all (pure storage/processing).
 */
public enum MinionWorkMode {

    FACING_ONLY,
    ARENA_ONLY,
    BOTH,
    NONE;

    /**
     * The hardcoded default work mode for each minion type, used when
     * minions.yml does not provide an override for that type.
     *
     * @param type the minion type
     * @return the default work mode
     */
    public static MinionWorkMode defaultFor(MinionType type) {
        return switch (type) {
            case MINER, QUARRY, LUMBERJACK, FARMER, COLLECTOR -> BOTH;
            case FISHERMAN -> ARENA_ONLY;
            case SMELTER, CHEST, SELL -> NONE;
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