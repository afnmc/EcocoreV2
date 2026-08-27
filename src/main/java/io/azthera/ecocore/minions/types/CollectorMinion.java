package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class CollectorMinion extends AbstractMinionHandler {
    public CollectorMinion() {
        super(MinionType.COLLECTOR, MinionProcessingType.ITEM_COLLECT, noEntities(), null, null);
    }
}
