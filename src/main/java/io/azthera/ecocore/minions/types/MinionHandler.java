package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

public interface MinionHandler {

    MinionType getType();

    MinionProcessingType getProcessingType();

    MinionWorkMode getWorkMode();

    int getEnergyCostPerAction();

    Set<Material> getTargetMaterials();

    Material resultFor(Material target);

    Set<EntityType> getTargetEntities();

    Material resultForEntity(EntityType type);

    Material getSeedItem();

    Material getPlantResult();

    List<Material> getPossibleCatches();

    default List<FishRarityTier> getRarityTiers() {
        return List.of();
    }

    default java.util.Map<Material, TreeSpeciesData> getTreeSpeciesData() {
        return java.util.Map.of();
    }

    default java.util.Map<Material, Material> getSmeltingRecipes() {
        return java.util.Map.of();
    }
}
