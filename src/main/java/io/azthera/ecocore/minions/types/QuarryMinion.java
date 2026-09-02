package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class QuarryMinion extends AbstractMinionHandler {
    public QuarryMinion() {
        super(MinionType.QUARRY, MinionProcessingType.BLOCK_BREAK, noEntities(), null, null);
    }
}