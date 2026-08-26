package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Consumes breeding food from its storage on a fixed cycle to manage
 * a herd of passive animals within its work radius, yielding a
 * representative resource item per cycle rather than simulating
 * individual offspring.
 */
public final class BreederMinion extends AbstractMinionHandler {

    public BreederMinion() {
        super(MinionType.BREEDER, MinionProcessingType.ENTITY_INTERACT, 3,
                noMaterials(),
                Map.ofEntries(
                        Map.entry(EntityType.COW, Material.LEATHER),
                        Map.entry(EntityType.PIG, Material.PORKCHOP),
                        Map.entry(EntityType.SHEEP, Material.WHITE_WOOL),
                        Map.entry(EntityType.CHICKEN, Material.EGG),
                        Map.entry(EntityType.HORSE, Material.LEATHER)
                ),
                Material.WHEAT, null, noCatches());
    }
}
