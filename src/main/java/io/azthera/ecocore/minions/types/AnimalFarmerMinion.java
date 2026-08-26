package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Gently harvests renewable resources from passive farm animals
 * (shearing, feathers) within its work radius, without harming them.
 */
public final class AnimalFarmerMinion extends AbstractMinionHandler {

    public AnimalFarmerMinion() {
        super(MinionType.ANIMAL_FARMER, MinionProcessingType.ENTITY_INTERACT, 2,
                noMaterials(),
                Map.ofEntries(
                        Map.entry(EntityType.SHEEP, Material.WHITE_WOOL),
                        Map.entry(EntityType.COW, Material.LEATHER),
                        Map.entry(EntityType.CHICKEN, Material.FEATHER),
                        Map.entry(EntityType.RABBIT, Material.RABBIT_HIDE)
                ),
                null, null, noCatches());
    }
}