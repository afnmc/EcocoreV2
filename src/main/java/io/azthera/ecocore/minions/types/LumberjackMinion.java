package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class LumberjackMinion extends AbstractMinionHandler {
    public LumberjackMinion() {
        super(MinionType.LUMBERJACK, MinionProcessingType.FARM_CYCLE, noEntities(), null, null);
    }
}
