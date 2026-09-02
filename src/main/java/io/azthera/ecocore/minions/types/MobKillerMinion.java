package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Map;

public final class MobKillerMinion extends AbstractMinionHandler {

    private static final Map<EntityType, Material> DROPS = Map.of(
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