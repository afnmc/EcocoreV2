// FILE: src/main/java/io/azthera/ecocore/minions/types/MinerMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Mines blocks configured under {@code targets.miner} in
 * minions.yml. Every mineable material - including ores, stone
 * variants, or anything an admin adds - is read live via {@link
 * AbstractMinionHandler#getTargetMaterials()} (Revisi 12).
 */
public final class MinerMinion extends AbstractMinionHandler {

    public MinerMinion() {
        super(MinionType.MINER, MinionProcessingType.BLOCK_BREAK, noEntities(), null, null);
    }
}