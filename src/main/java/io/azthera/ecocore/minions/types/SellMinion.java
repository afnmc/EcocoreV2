package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class SellMinion extends AbstractMinionHandler {
    public SellMinion() {
        super(MinionType.SELL, MinionProcessingType.SELL_ONLY, noEntities(), null, null);
    }
}
