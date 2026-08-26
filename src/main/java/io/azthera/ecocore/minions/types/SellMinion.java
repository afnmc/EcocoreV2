// FILE: src/main/java/io/azthera/ecocore/minions/types/SellMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Automatically sells whatever's in its storage on a scheduled
 * interval (handled by {@code SellManager}, not the per-tick AI
 * pass). Does not work an area (workMode NONE).
 */
public final class SellMinion extends AbstractMinionHandler {

    public SellMinion() {
        super(MinionType.SELL, MinionProcessingType.SELL_ONLY, noEntities(), null, null);
    }
}