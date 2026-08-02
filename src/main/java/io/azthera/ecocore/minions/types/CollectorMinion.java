package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Picks up dropped item entities within its work radius into its
 * storage. Has no fixed target material/entity table - the
 * controller scans for any nearby dropped {@code Item} entity.
 */
public final class CollectorMinion extends AbstractMinionHandler {

    public CollectorMinion() {
        super(MinionType.COLLECTOR, MinionProcessingType.ITEM_PICKUP, 1,
                noMaterials(), noEntities(), null, null, noCatches());
    }
}