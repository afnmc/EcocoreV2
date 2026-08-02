package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Attacks hostile mobs within its work radius and collects a
 * representative drop item.
 */
public final class MobKillerMinion extends AbstractMinionHandler {

    public MobKillerMinion() {
        super(MinionType.MOB_KILLER, MinionProcessingType.ENTITY_INTERACT, 4,
                noMaterials(),
                Map.ofEntries(
                        Map.entry(EntityType.ZOMBIE, Material.ROTTEN_FLESH),
                        Map.entry(EntityType.SKELETON, Material.BONE),
                        Map.entry(EntityType.SPIDER, Material.STRING),
                        Map.entry(EntityType.CREEPER, Material.GUNPOWDER),
                        Map.entry(EntityType.ENDERMAN, Material.ENDER_PEARL),
                        Map.entry(EntityType.WITCH, Material.GLOWSTONE_DUST)
                ),
                null, null, noCatches());
    }
}