package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class ChestMinion extends AbstractMinionHandler {
    public ChestMinion() {
        super(MinionType.CHEST, MinionProcessingType.CHEST_DETECT, noEntities(), null, null);
    }
}