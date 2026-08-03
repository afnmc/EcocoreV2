package io.azthera.ecocore.minions.types;

/**
 * Categorizes how a minion type performs its work, so
 * {@code MinionAiController} knows which behavior branch to run for
 * a given {@link io.azthera.ecocore.model.MinionType}.
 */
public enum MinionProcessingType {

    /** Breaks a nearby matching block and stores the resulting item (Miner, Lumberjack, Farmer, Harvester, Quarry). */
    BLOCK_BREAK,

    /** Consumes a seed item from storage and produces a harvested item on a fixed cycle (Planter). */
    BLOCK_PLACE,

    /** Interacts with a nearby matching entity and stores a resulting item (Mob Killer, Animal Farmer, Breeder). */
    ENTITY_INTERACT,

    /** Picks up nearby dropped item entities AND pulls from nearby owned minions' storage into its own (Collector). */
    ITEM_PICKUP,

    /** Produces a random catch from a configured table on a fixed cycle (Fishing). */
    FISHING,

    /** Converts a raw input item already in storage into a smelted/crafted output item (Smelter - also absorbs what used to be the separate Crafter minion). */
    INTERNAL_SMELT,

    /** Sells sellable items already in storage directly to the owner's balance (Seller). */
    INTERNAL_SELL,

    /** Performs no world action; exists purely to provide extra shared storage (Storage). */
    PASSIVE
}
