// FILE: src/main/java/io/azthera/ecocore/minions/types/MinionProcessingType.java
package io.azthera.ecocore.minions.types;

/**
 * Categorizes how a minion type performs its work, so {@code
 * MinionAiController} knows which behavior branch to run for a given
 * {@link io.azthera.ecocore.model.MinionType}.
 */
public enum MinionProcessingType {

    /** Breaks a nearby matching block and stores the resulting item (Miner, Quarry). */
    BLOCK_BREAK,

    /** Plants/harvests/replants crops or trees on a cycle (Farmer, Lumberjack). */
    FARM_CYCLE,

    /** Interacts with a nearby matching entity and stores a resulting item (Mob Killer). */
    ENTITY_INTERACT,

    /**
     * Picks up nearby dropped item entities within radius (Collector).
     * Revisi 9: no longer also pulls from other minions' storage - that
     * cross-minion movement is exclusively the Connector Network's job now.
     */
    ITEM_COLLECT,

    /** Produces a random weighted-rarity catch on a cycle (Fisherman - Revisi 8). */
    FISHING,

    /** Converts a configured raw input item in storage into a smelted output item (Smelter - Revisi 5). */
    INTERNAL_SMELT,

    /** Sells sellable items already in storage on a schedule (Sell). */
    SELL_ONLY,

    /**
     * Detects an adjacent chest (single vs double) once at placement
     * time and otherwise does no ongoing per-tick work (Chest - Revisi 2).
     */
    CHEST_DETECT,

    /** Performs no world action at all; exists purely as extra storage capacity. */
    NONE
}