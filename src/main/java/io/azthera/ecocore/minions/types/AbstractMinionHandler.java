package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base implementation of {@link MinionHandler} backed by static
 * lookup maps, so each concrete minion type class only needs to
 * declare the fields relevant to its own processing type.
 */
public abstract class AbstractMinionHandler implements MinionHandler {

    private final MinionType type;
    private final MinionProcessingType processingType;
    private final int energyCostPerAction;
    private final Map<Material, Material> materialResults;
    private final Map<EntityType, Material> entityResults;
    private final Material seedItem;
    private final Material plantResult;
    private final List<Material> possibleCatches;

    /**
     * Creates a minion handler.
     *
     * @param type                the minion type this handler implements
     * @param processingType      how this minion type performs its work
     * @param energyCostPerAction the per-action energy cost
     * @param materialResults     target/input material to output material mapping
     * @param entityResults       target entity type to output material mapping
     * @param seedItem            consumed seed/input material, may be {@code null}
     * @param plantResult         planting cycle output material, may be {@code null}
     * @param possibleCatches     fishing cycle possible outcomes, may be empty
     */
    protected AbstractMinionHandler(MinionType type, MinionProcessingType processingType, int energyCostPerAction,
                                     Map<Material, Material> materialResults, Map<EntityType, Material> entityResults,
                                     Material seedItem, Material plantResult, List<Material> possibleCatches) {
        this.type = type;
        this.processingType = processingType;
        this.energyCostPerAction = energyCostPerAction;
        this.materialResults = materialResults;
        this.entityResults = entityResults;
        this.seedItem = seedItem;
        this.plantResult = plantResult;
        this.possibleCatches = possibleCatches;
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
    public int getEnergyCostPerAction() {
        return energyCostPerAction;
    }

    @Override
    public Set<Material> getTargetMaterials() {
        return materialResults.keySet();
    }

    @Override
    public Material resultFor(Material target) {
        return materialResults.getOrDefault(target, target);
    }

    @Override
    public Set<EntityType> getTargetEntities() {
        return entityResults.keySet();
    }

    @Override
    public Material resultForEntity(EntityType type) {
        return entityResults.get(type);
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
        return possibleCatches;
    }

    /**
     * Convenience factory for handlers with no entity/seed/fishing
     * component (block-based types).
     *
     * @param materialResults target block to output item mapping
     * @return an empty entity map, for use in constructors
     */
    protected static Map<EntityType, Material> noEntities() {
        return Collections.emptyMap();
    }

    /**
     * Convenience factory for handlers with no block/input component
     * (entity-based types).
     *
     * @return an empty material map, for use in constructors
     */
    protected static Map<Material, Material> noMaterials() {
        return Collections.emptyMap();
    }

    /**
     * Convenience factory for handlers with no fishing catch table.
     *
     * @return an empty catch list, for use in constructors
     */
    protected static List<Material> noCatches() {
        return Collections.emptyList();
    }
}