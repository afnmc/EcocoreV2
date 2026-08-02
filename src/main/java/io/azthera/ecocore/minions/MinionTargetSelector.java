package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the best block or entity target for a minion's next action,
 * within its work radius, honoring the configured obstacle-avoidance
 * and target-selection-strategy settings from {@code minions.yml}.
 */
public final class MinionTargetSelector {

    private final MinionsConfig minionsConfig;

    /**
     * Creates a target selector.
     *
     * @param minionsConfig resolved minions.yml configuration (AI strategy settings)
     */
    public MinionTargetSelector(MinionsConfig minionsConfig) {
        this.minionsConfig = minionsConfig;
    }

    /**
     * Finds the nearest block within radius matching the handler's
     * target materials.
     *
     * @param origin  the minion's current location
     * @param radius  the effective work radius to search within
     * @param handler the minion's handler, providing the target material set
     * @return the nearest matching block, or empty if none found
     */
    public Optional<Block> findNearestBlock(Location origin, int radius, MinionHandler handler) {
        if (handler.getTargetMaterials().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }

        Block nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -Math.min(radius, 6); dy <= Math.min(radius, 6); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = origin.getWorld().getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    if (!handler.getTargetMaterials().contains(block.getType())) {
                        continue;
                    }

                    if (minionsConfig.isObstacleAvoidanceEnabled() && !hasLineOfSight(origin, block.getLocation())) {
                        continue;
                    }

                    double distanceSq = block.getLocation().distanceSquared(origin);
                    if (distanceSq < nearestDistanceSq) {
                        nearestDistanceSq = distanceSq;
                        nearest = block;
                    }
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Finds the best living entity within radius matching the
     * handler's target entity types, chosen according to the
     * configured target-selection-strategy.
     *
     * @param origin  the minion's current location
     * @param radius  the effective work radius to search within
     * @param handler the minion's handler, providing the target entity type set
     * @return the selected entity, or empty if none found
     */
    public Optional<LivingEntity> findBestEntity(Location origin, int radius, MinionHandler handler) {
        if (handler.getTargetEntities().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }

        BoundingBox box = BoundingBox.of(origin, radius, radius, radius);
        List<Entity> nearby = origin.getWorld().getNearbyEntities(box).stream()
                .filter(entity -> entity instanceof LivingEntity)
                .filter(entity -> handler.getTargetEntities().contains(entity.getType()))
                .toList();

        if (nearby.isEmpty()) {
            return Optional.empty();
        }

        Comparator<Entity> comparator = "NEAREST_HIGHEST_VALUE".equals(minionsConfig.getTargetSelectionStrategy())
                ? Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin))
                : Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin));

        return nearby.stream()
                .min(comparator)
                .map(entity -> (LivingEntity) entity);
    }

    private boolean hasLineOfSight(Location from, Location to) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        var result = from.getWorld().rayTraceBlocks(from, to.toVector().subtract(from.toVector()).normalize(),
                from.distance(to));
        return result == null || result.getHitBlock() == null;
    }
}