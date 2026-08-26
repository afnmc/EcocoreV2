// FILE: src/main/java/io/azthera/ecocore/minions/types/QuarryMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Clears general terrain blocks configured under {@code
 * targets.quarry} in minions.yml (stone, dirt, gravel, sand, etc.).
 */
public final class QuarryMinion extends AbstractMinionHandler {

    public QuarryMinion() {
        super(MinionType.QUARRY, MinionProcessingType.BLOCK_BREAK, noEntities(), null, null);
    }
}