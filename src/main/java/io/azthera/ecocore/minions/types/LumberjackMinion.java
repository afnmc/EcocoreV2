// FILE: src/main/java/io/azthera/ecocore/minions/types/LumberjackMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Chops trees and auto-replants saplings within its work radius
 * (Revisi 7), respecting per-species spacing/canopy rules (Revisi
 * 3). Species drop tables are configured under {@code
 * lumberjack.tree-species} in minions.yml, read live via {@link
 * AbstractMinionHandler#getTreeSpeciesData()}.
 */
public final class LumberjackMinion extends AbstractMinionHandler {

    public LumberjackMinion() {
        super(MinionType.LUMBERJACK, MinionProcessingType.FARM_CYCLE, noEntities(), null, null);
    }
}