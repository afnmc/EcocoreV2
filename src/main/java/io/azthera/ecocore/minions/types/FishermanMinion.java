// FILE: src/main/java/io/azthera/ecocore/minions/types/FishermanMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Fishes near water, producing catches from weighted rarity tiers
 * (Revisi 8) configured under {@code fisherman.rarity} in
 * minions.yml, read live via {@link AbstractMinionHandler#getRarityTiers()}.
 */
public final class FishermanMinion extends AbstractMinionHandler {

    public FishermanMinion() {
        super(MinionType.FISHERMAN, MinionProcessingType.FISHING, noEntities(), null, null);
    }
}