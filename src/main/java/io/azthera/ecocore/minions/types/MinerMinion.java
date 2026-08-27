package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

public final class MinerMinion extends AbstractMinionHandler {
    public MinerMinion() {
        super(MinionType.MINER, MinionProcessingType.BLOCK_BREAK, noEntities(), null, null);
    }
}
