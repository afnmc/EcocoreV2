// FILE: src/main/java/io/azthera/ecocore/minions/types/AbstractMinionHandler.java
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
 * This means a {@code /ecocore reload} that re-parses {@code
 * minions.yml} takes effect immediately for every handler without
 * needing to reconstruct the handler objects themselves - a fresh
 * call to {@link #getTargetMaterials()} etc. always reflects
 * whatever {@link MinionsConfig} instance is currently wired in via
 * {@link io.azthera.ecocore.EcoCorePlugin#getMinionsConfig()}.
 *
 * Only the parts of a handler that are NOT reasonably
 * config-driven (its {@link MinionType}, its {@link
 * MinionProcessingType}, and its entity-interaction/seed/plant
 * mappings, which are structural rather than tunable) stay as
 * constructor-supplied fixed values.
 */
public abstract class AbstractMinionHandler implements MinionHandler {

    private final MinionType type;
    private final MinionProcessingType processingType;
    private final MapEntityType, Material> entityResults;
    private final Material seedItem;
    private final Material plantResult;

    /**
     * Creates a minion handler.
     *
     * @param type the minion type this handler implements
     * @param processingType how this minion type performs its work
     * @param entityResults target entity type to output material mapping (structural, not yml-driven)
     * @param seedItem consumed seed/input material, may be {@code null}
     * @param plantResult planting cycle output material, may be {@code null}
     */
    protected AbstractMinionHandler(MinionType type, MinionProcessingType processingType,
                                     MapEntityType, Material> entityResults,
                                     Material seedItem, Material plantResult) {
        this.type = type;
        this.processingType = processingType;
        this.entityResults = entityResults;
        this.seedItem = seedItem;
        this.plantResult = plantResult;
    }

    /**
     * Resolves the live {@link MinionsConfig} instance. Package-visible
     * indirection point so a future test harness could substitute a
     * fixed config without touching every concrete handler.
     */
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

    /**
     * Revisi 12: reads live from {@code minions.yml targets.} -
     * e.g. adding BEDROCK there makes the Miner able to mine bedrock
     * with no code change, and removing it takes that ability away.
     */
    @Override
    public SetMaterial> getTargetMaterials() {
        return config().getTargetBlocksFor(type);
    }

    @Override
    public Material resultFor(Material target) {
        // For SMELTER, "target" is the smelting input and the smelting
        // recipe map is the authority; for block-break types, breaking a
        // configured target block simply yields itself (no transformation
        // table needed - Revisi 12's targets list already declares exactly
        // what's mineable, and mined blocks drop as themselves).
        if (type == MinionType.SMELTER) {
            return config().getSmeltingRecipes().getOrDefault(target, target);
        }
        return target;
    }

    @Override
    public SetEntityType> getTargetEntities() {
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
    public ListMaterial> getPossibleCatches() {
        return getRarityTiers().stream()
                .flatMap(tier -> tier.pool().stream())
                .distinct()
                .toList();
    }

    @Override
    public ListFishRarityTier> getRarityTiers() {
        return type == MinionType.FISHERMAN ? config().getFishRarityTiers() : List.of();
    }

    @Override
    public MapMaterial, TreeSpeciesData> getTreeSpeciesData() {
        return type == MinionType.LUMBERJACK ? config().getTreeSpeciesData() : Map.of();
    }

    @Override
    public MapMaterial, Material> getSmeltingRecipes() {
        return type == MinionType.SMELTER ? config().getSmeltingRecipes() : Map.of();
    }

    protected static MapEntityType, Material> noEntities() {
        return Collections.emptyMap();
    }
}