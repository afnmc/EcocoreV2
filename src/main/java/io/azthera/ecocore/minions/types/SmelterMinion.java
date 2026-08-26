// FILE: src/main/java/io/azthera/ecocore/minions/types/SmelterMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Smelts raw ore/input materials into refined output (Revisi 5:
 * RAW_IRON -> IRON_INGOT etc., never a downgrade chain), using
 * recipes configured under {@code smelter.recipes} in minions.yml,
 * read live via {@link AbstractMinionHandler#getSmeltingRecipes()}.
 * Does not work an area (workMode NONE) - it only processes items
 * sitting in its own Zone A input slots.
 */
public final class SmelterMinion extends AbstractMinionHandler {

    public SmelterMinion() {
        super(MinionType.SMELTER, MinionProcessingType.INTERNAL_SMELT, noEntities(), null, null);
    }
}