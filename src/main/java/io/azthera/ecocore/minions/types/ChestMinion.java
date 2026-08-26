// FILE: src/main/java/io/azthera/ecocore/minions/types/ChestMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Detects an adjacent chest (single vs double) once at placement
 * time (Revisi 2) - purely a passive storage-adjacency indicator,
 * does no ongoing work (workMode NONE).
 */
public final class ChestMinion extends AbstractMinionHandler {

    public ChestMinion() {
        super(MinionType.CHEST, MinionProcessingType.CHEST_DETECT, noEntities(), null, null);
    }
}