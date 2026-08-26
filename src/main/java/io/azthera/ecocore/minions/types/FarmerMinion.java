// FILE: src/main/java/io/azthera/ecocore/minions/types/FarmerMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;

/**
 * Plants, harvests, and auto-replants crops within its work radius,
 * using two zones (Revisi 4): Zone A (seeds) and Zone B (produce).
 * Crop list is configured under {@code targets.farmer} in
 * minions.yml (Revisi 12). The seed item and its harvest output are
 * structural per-crop-family mappings handled directly in {@code
 * MinionAiController.resolveCropBlockForSeed}, not here - this
 * handler's {@code seedItem}/{@code plantResult} fields represent
 * only the primary crop (wheat) for icon/tooltip purposes.
 */
public final class FarmerMinion extends AbstractMinionHandler {

    public FarmerMinion() {
        super(MinionType.FARMER, MinionProcessingType.FARM_CYCLE, noEntities(),
                Material.WHEAT_SEEDS, Material.WHEAT);
    }
}