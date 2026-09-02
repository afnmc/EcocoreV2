package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class FishermanMinion extends AbstractMinionHandler {
    public FishermanMinion() {
        super(MinionType.FISHERMAN, MinionProcessingType.FISHING, noEntities(), null, null);
    }
}