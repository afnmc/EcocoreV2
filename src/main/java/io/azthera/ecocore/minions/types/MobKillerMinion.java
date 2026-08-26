// FILE: src/main/java/io/azthera/ecocore/minions/types/MobKillerMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Attacks and defeats configured hostile mob types within its work
 * radius, dropping a configured item per kill. The entity target
 * list is structural (not read from minions.yml targets.* since it
 * pairs each entity with a specific drop, unlike the simpler
 * block-target lists) - see {@code targets.mob_killer.entities} in
 * minions.yml for the raw entity list used by placement/shop
 * tooltips, while this mapping controls actual drop resolution.
 */
public final class MobKillerMinion extends AbstractMinionHandler {

    private static final MapEntityType, Material> DROPS = Map.of(
            EntityType.ZOMBIE, Material.ROTTEN_FLESH,
            EntityType.SKELETON, Material.BONE,
            EntityType.SPIDER, Material.STRING,
            EntityType.CREEPER, Material.GUNPOWDER,
            EntityType.ENDERMAN, Material.ENDER_PEARL
    );

    public MobKillerMinion() {
        super(MinionType.MOB_KILLER, MinionProcessingType.ENTITY_INTERACT, DROPS, null, null);
    }
}