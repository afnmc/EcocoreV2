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
 * (ARENA mode) or along a STRICT 1-WIDE STRAIGHT LINE in its locked
 * facing direction (FACING mode - bug-fix round: narrowed from a
 * wide slab to a strict straight line per explicit instruction).
 * Minions never move from where they were placed and never use
 * pathfinding of any kind.
 *
 * <p>Deliberately does NOT require a clear line of sight to the
 * target: for block-breaking minions especially, the whole point is
 * reaching into solid terrain (ore buried in stone), where a strict
 * line-of-sight check would almost always fail since the target is,
 * by definition, surrounded by more of the same solid material.
 */
public final class MinionTargetSelector {

    public Optional<Block> findNearestBlockInArena(Location origin, int radius, MinionHandler handler) {
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
     * Finds the nearest matching block along a STRICT 1-WIDE straight
     * line extending outward from the minion in its locked facing
     * direction (FACING mode) - no sideways spread at all, exactly
     * the column of blocks directly ahead of the minion.
     */
    public Optional<Block> findNearestBlockInFacingLine(Location origin, BlockFace facing, int radius,
                                                         MinionHandler handler) {
        if (handler.getTargetMaterials().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }
        if (facing.getModX() == 0 && facing.getModZ() == 0) {
            return Optional.empty();
        }
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        Block nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (int depth = 1; depth <= radius; depth++) {
            int x = baseX + facing.getModX() * depth;
            int z = baseZ + facing.getModZ() * depth;
            for (int dy = -Math.min(radius, 6); dy <= Math.min(radius, 6); dy++) {
                Block block = origin.getWorld().getBlockAt(x, baseY + dy, z);
                if (!handler.getTargetMaterials().contains(block.getType())) {
                    continue;
                }
                double distanceSq = block.getLocation().distanceSquared(origin);
                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearest = block;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

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
     * Finds the nearest living entity along a STRICT 1-WIDE straight
     * line ahead of the minion (FACING mode) using a thin bounding
     * box rather than a wide one.
     */
    public Optional<LivingEntity> findBestEntityInFacingLine(Location origin, BlockFace facing, int radius,
                                                              MinionHandler handler) {
        if (handler.getTargetEntities().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }
        if (facing.getModX() == 0 && facing.getModZ() == 0) {
            return Optional.empty();
        }
        Location center = origin.clone().add(facing.getModX() * (radius / 2.0), 0, facing.getModZ() * (radius / 2.0));
        double halfLength = radius / 2.0 + 0.5;
        double halfWidth = 0.6; // just wide enough to cover a single block column
        BoundingBox box = facing.getModX() != 0
                ? BoundingBox.of(center, halfLength, Math.min(radius, 6), halfWidth)
                : BoundingBox.of(center, halfWidth, Math.min(radius, 6), halfLength);
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