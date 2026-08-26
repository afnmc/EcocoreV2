// FILE: src/main/java/io/azthera/ecocore/minions/types/MinionHandler.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * Defines a minion type's targets and behavior. Every list/set this
 * interface exposes is expected to be sourced from {@code
 * MinionsConfig} (Revisi 12) rather than hardcoded - the interface
 * itself doesn't care where the data came from, but no implementation
 * should ever return a fixed, unconfigurable collection for these methods.
 */
public interface MinionHandler {

    MinionType getType();

    MinionProcessingType getProcessingType();

    /**
     * The work mode this handler currently uses (facing-only, arena,
     * both, or none). For BOTH-mode types, callers should also check
     * {@code MinionData.isUseArenaMode()} to know which of the two is
     * actually active for a specific placed minion.
     *
     * @return the minion type's work mode
     */
    MinionWorkMode getWorkMode();

    int getEnergyCostPerAction();

    SetMaterial> getTargetMaterials();

    Material resultFor(Material target);

    SetEntityType> getTargetEntities();

    Material resultForEntity(EntityType type);

    Material getSeedItem();

    Material getPlantResult();

    /**
     * The possible random catch outcomes for a fishing cycle, kept
     * for backward interface compatibility. Fisherman uses
     * {@link #getRarityTiers()} instead (Revisi 8) which supersedes
     * this for actual catch resolution.
     *
     * @return the possible catch materials, empty if not applicable
     */
    ListMaterial> getPossibleCatches();

    /**
     * Weighted rarity tiers for a fishing catch (Revisi 8). Empty for
     * every type except Fisherman.
     *
     * @return the configured rarity tiers, in no particular order
     */
    default ListFishRarityTier> getRarityTiers() {
        return List.of();
    }

    /**
     * Per-species tree data for lumberjack drop resolution (Revisi
     * 7). Empty for every type except Lumberjack.
     *
     * @return log material to species-data mapping
     */
    default java.util.MapMaterial, TreeSpeciesData> getTreeSpeciesData() {
        return java.util.Map.of();
    }

    /**
     * Configured smelting recipes, input material to output material
     * (Revisi 5). Empty for every type except Smelter.
     *
     * @return the smelting recipe map
     */
    default java.util.MapMaterial, Material> getSmeltingRecipes() {
        return java.util.Map.of();
    }
}