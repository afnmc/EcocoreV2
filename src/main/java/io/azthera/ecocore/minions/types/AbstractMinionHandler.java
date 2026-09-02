package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base implementation of {@link MinionHandler} that delegates every
 * config-driven list/map to a live {@link MinionsConfig} reference
 * rather than a snapshot captured at construction time (Revisi 12).
 * A {@code /ecocore reload} that re-parses {@code minions.yml} takes
 * effect immediately for every handler.
 */
public abstract class AbstractMinionHandler implements MinionHandler {

    private final MinionType type;
    private final MinionProcessingType processingType;
    private final Map<EntityType, Material> entityResults;
    private final Material seedItem;
    private final Material plantResult;

    protected AbstractMinionHandler(MinionType type, MinionProcessingType processingType,
                                     Map<EntityType, Material> entityResults,
                                     Material seedItem, Material plantResult) {
        this.type = type;
        this.processingType = processingType;
        this.entityResults = entityResults;
        this.seedItem = seedItem;
        this.plantResult = plantResult;
    }

    private MinionsConfig config() {
        return io.azthera.ecocore.EcoCorePlugin.getInstance().getMinionsConfig();
    }

    @Override
    public MinionType getType() {
        return type;
    }

    @Override
    public MinionProcessingType getProcessingType() {
        return processingType;
    }

    @Override
    public MinionWorkMode getWorkMode() {
        return config().getWorkModeFor(type);
    }

    @Override
    public int getEnergyCostPerAction() {
        return config().getEnergyDrainPerAction();
    }

    @Override
    public Set<Material> getTargetMaterials() {
        return config().getTargetBlocksFor(type);
    }

    @Override
    public Material resultFor(Material target) {
        if (type == MinionType.SMELTER) {
            return config().getSmeltingRecipes().getOrDefault(target, target);
        }
        return target;
    }

    @Override
    public Set<EntityType> getTargetEntities() {
        return entityResults.keySet();
    }

    @Override
    public Material resultForEntity(EntityType entityType) {
        return entityResults.get(entityType);
    }

    @Override
    public Material getSeedItem() {
        return seedItem;
    }

    @Override
    public Material getPlantResult() {
        return plantResult;
    }

    @Override
    public List<Material> getPossibleCatches() {
        return getRarityTiers().stream()
                .flatMap(tier -> tier.pool().stream())
                .distinct()
                .toList();
    }

    @Override
    public List<FishRarityTier> getRarityTiers() {
        return type == MinionType.FISHERMAN ? config().getFishRarityTiers() : List.of();
    }

    @Override
    public Map<Material, TreeSpeciesData> getTreeSpeciesData() {
        return type == MinionType.LUMBERJACK ? config().getTreeSpeciesData() : Map.of();
    }

    @Override
    public Map<Material, Material> getSmeltingRecipes() {
        return type == MinionType.SMELTER ? config().getSmeltingRecipes() : Map.of();
    }

    protected static Map<EntityType, Material> noEntities() {
        return Collections.emptyMap();
    }
}