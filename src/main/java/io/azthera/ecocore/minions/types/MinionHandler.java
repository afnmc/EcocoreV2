package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * Defines how a single minion type interacts with the world: which
 * blocks/entities it targets, what those targets yield, and (for
 * planter/fishing types) its special-cased inputs/outputs.
 *
 * <p>Yield mappings are intentionally simplified relative to vanilla
 * mechanics (e.g. breeding yields a representative resource item
 * rather than spawning a baby animal) so the system stays fully
 * data-driven and predictable for AI-priced economy integration.
 */
public interface MinionHandler {

    /**
     * The minion type this handler implements.
     *
     * @return the minion type
     */
    MinionType getType();

    /**
     * How this minion type performs its work.
     *
     * @return the processing type
     */
    MinionProcessingType getProcessingType();

    /**
     * The energy cost of a single action for this minion type.
     *
     * @return the per-action energy cost
     */
    int getEnergyCostPerAction();

    /**
     * The set of block materials this minion targets (for
     * {@link MinionProcessingType#BLOCK_BREAK}), or the set of raw
     * input item materials it processes (for
     * {@link MinionProcessingType#INTERNAL_SMELT} /
     * {@link MinionProcessingType#INTERNAL_CRAFT}).
     *
     * @return the target/input materials, empty if not applicable to this type
     */
    Set<Material> getTargetMaterials();

    /**
     * The item material produced from processing a given target/input
     * material, falling back to the input itself if unmapped.
     *
     * @param target the target/input material
     * @return the resulting output material
     */
    Material resultFor(Material target);

    /**
     * The set of entity types this minion targets (for
     * {@link MinionProcessingType#ENTITY_INTERACT}).
     *
     * @return the target entity types, empty if not applicable
     */
    Set<EntityType> getTargetEntities();

    /**
     * The item material produced from interacting with a given entity type.
     *
     * @param type the entity type
     * @return the resulting output material, or {@code null} if unmapped
     */
    Material resultForEntity(EntityType type);

    /**
     * The seed/input item this minion consumes per planting or
     * breeding cycle (for {@link MinionProcessingType#BLOCK_PLACE}
     * and the Breeder's {@link MinionProcessingType#ENTITY_INTERACT} cycle).
     *
     * @return the consumed seed/input material, or {@code null} if not applicable
     */
    Material getSeedItem();

    /**
     * The item produced by a completed planting cycle (for
     * {@link MinionProcessingType#BLOCK_PLACE}).
     *
     * @return the planted-and-harvested output material, or {@code null} if not applicable
     */
    Material getPlantResult();

    /**
     * The possible random catch outcomes for a fishing cycle (for
     * {@link MinionProcessingType#FISHING}).
     *
     * @return the possible catch materials, empty if not applicable
     */
    List<Material> getPossibleCatches();
}