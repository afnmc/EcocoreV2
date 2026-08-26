// FILE: src/main/java/io/azthera/ecocore/minions/MinionTargetSelector.java
package io.azthera.ecocore.minions;

import io.azthera.ecocore.minions.types.MinionHandler;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the nearest matching block or entity for a STATIONARY
 * minion's next action, either within a full 360-degree radius
 * (ARENA mode) or along a single straight line in its locked facing
 * direction (FACING mode - Revisi 1/2). Minions never move from
 * where they were placed and never use pathfinding of any kind.
 *
 * Deliberately does NOT require a clear line of sight to the
 * target: for block-breaking minions especially, the whole point is
 * reaching into solid terrain (ore buried in stone), where a strict
 * line-of-sight check would almost always fail since the target is,
 * by definition, surrounded by more of the same solid material.
 */
public final class MinionTargetSelector {

    /**
     * Finds the nearest block within a 360-degree radius matching the
     * handler's target materials (ARENA mode).
     *
     * @param origin the minion's (fixed) location
     * @param radius the effective work radius to search within
     * @param handler the minion's handler, providing the target material set
     * @return the nearest matching block, or empty if none found
     */
    public Optional<Block> findNearestBlockInArena(Location origin, int radius, MinionHandler handler) {
        if (handler.getTargetMaterials().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }
        Block nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        for (int dx = -radius; dx ; dx++) {
            for (int dy = -Math.min(radius, 6); dy .min(radius, 6); dy++) {
                for (int dz = -radius; dz ; dz++) {
                    Block block = origin.getWorld().getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    if (!handler.getTargetMaterials().contains(block.getType())) {
                        continue;
                    }
                    double distanceSq = block.getLocation().distanceSquared(origin);
                    if (distanceSq ) {
                        nearestDistanceSq = distanceSq;
                        nearest = block;
                    }
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Finds the nearest matching block within a rectangular slab
     * extending outward from the minion in its locked facing
     * direction (FACING mode - Revisi 1/2), rather than scanning a
     * full sphere. The slab is {@code radius} blocks deep along the
     * facing axis and {@code radius} blocks wide/tall perpendicular
     * to it, so a Miner facing east tunnels a wide east-facing shaft
     * instead of a single one-block-wide line.
     *
     * @param origin the minion's (fixed) location
     * @param facing the locked cardinal direction to search in front of
     * @param radius how deep/wide the search slab extends
     * @param handler the minion's handler, providing the target material set
     * @return the nearest matching block in front of the minion, or empty if none found
     */
    public Optional<Block> findNearestBlockInFacingSlab(Location origin, BlockFace facing, int radius,
                                                          MinionHandler handler) {
        if (handler.getTargetMaterials().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        Block nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (int depth = 1; depth ; depth++) {
            int centerX = baseX + facing.getModX() * depth;
            int centerZ = baseZ + facing.getModZ() * depth;
            for (int side = -radius; side ; side++) {
                for (int dy = -Math.min(radius, 6); dy .min(radius, 6); dy++) {
                    int x = facing.getModX() == 0 ? centerX + side : centerX;
                    int z = facing.getModZ() == 0 ? centerZ + side : centerZ;
                    Block block = origin.getWorld().getBlockAt(x, baseY + dy, z);
                    if (!handler.getTargetMaterials().contains(block.getType())) {
                        continue;
                    }
                    double distanceSq = block.getLocation().distanceSquared(origin);
                    if (distanceSq ) {
                        nearestDistanceSq = distanceSq;
                        nearest = block;
                    }
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Finds the nearest living entity within a 360-degree radius
     * matching the handler's target entity types (ARENA mode).
     *
     * @param origin the minion's (fixed) location
     * @param radius the effective work radius to search within
     * @param handler the minion's handler, providing the target entity type set
     * @return the selected entity, or empty if none found
     */
    public Optional<LivingEntity> findBestEntityInArena(Location origin, int radius, MinionHandler handler) {
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
        return nearby.stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
                .map(entity -> (LivingEntity) entity);
    }

    /**
     * Finds the nearest living entity within a facing-direction slab
     * (FACING mode), using the same slab shape as
     * {@link #findNearestBlockInFacingSlab}.
     *
     * @param origin the minion's (fixed) location
     * @param facing the locked cardinal direction to search in front of
     * @param radius how deep/wide the search slab extends
     * @param handler the minion's handler, providing the target entity type set
     * @return the selected entity, or empty if none found
     */
    public Optional<LivingEntity> findBestEntityInFacingSlab(Location origin, BlockFace facing, int radius,
                                                               MinionHandler handler) {
        if (handler.getTargetEntities().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }
        double halfWidth = radius;
        Location center = origin.clone().add(facing.getModX() * (radius / 2.0), 0, facing.getModZ() * (radius / 2.0));
        BoundingBox box = BoundingBox.of(center, halfWidth, Math.min(radius, 6), halfWidth);
        List<Entity> nearby = origin.getWorld().getNearbyEntities(box).stream()
                .filter(entity -> entity instanceof LivingEntity)
                .filter(entity -> handler.getTargetEntities().contains(entity.getType()))
                .toList();
        if (nearby.isEmpty()) {
            return Optional.empty();
        }
        return nearby.stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
                .map(entity -> (LivingEntity) entity);
    }
}