package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class SmelterMinion extends AbstractMinionHandler {
    public SmelterMinion() {
        super(MinionType.SMELTER, MinionProcessingType.INTERNAL_SMELT, noEntities(), null, null);
    }
}