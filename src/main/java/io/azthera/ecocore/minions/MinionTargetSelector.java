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
 * minion's next action, within its work radius. Minions never move
 * from where they were placed - this simply scans the surrounding
 * radius and acts on whatever's nearest, the same way a hopper or
 * dispenser reaches into its immediate surroundings without walking
 * anywhere.
 *
 * <p>Deliberately does NOT require a clear line of sight to the
 * target: for block-breaking minions especially, the whole point is
 * reaching into solid terrain (ore buried in stone), where a strict
 * line-of-sight check would almost always fail since the target is,
 * by definition, surrounded by more of the same solid material.
 */
public final class MinionTargetSelector {

    /**
     * Finds the nearest block within radius matching the handler's
     * target materials.
     *
     * @param origin  the minion's (fixed) location
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
     * Finds the first matching block along a straight line extending
     * from the minion's location in a single facing direction, used
     * by "facing mode" block-break minions (e.g. a Miner tunneling
     * straight ahead) as an alternative to the default nearest-in-
     * radius search. Unlike {@link #findNearestBlock}, this does not
     * scan sideways or vertically at all - it only ever advances
     * along one axis.
     *
     * @param origin      the minion's (fixed) location
     * @param facing      the direction to scan in
     * @param maxDistance how many blocks to scan before giving up
     * @param handler     the minion's handler, providing the target material set
     * @return the first matching block along the line, or empty if none found
     */
    public Optional<Block> findBlockInFacingDirection(Location origin, BlockFace facing, int maxDistance,
                                                       MinionHandler handler) {
        if (handler.getTargetMaterials().isEmpty() || origin.getWorld() == null) {
            return Optional.empty();
        }

        Block cursor = origin.getBlock();
        for (int step = 1; step <= maxDistance; step++) {
            cursor = cursor.getRelative(facing);
            if (handler.getTargetMaterials().contains(cursor.getType())) {
                return Optional.of(cursor);
            }
        }

        return Optional.empty();
    }

    /**
     * Finds the nearest living entity within radius matching the
     * handler's target entity types.
     *
     * @param origin  the minion's (fixed) location
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

        return nearby.stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
                .map(entity -> (LivingEntity) entity);
    }
            }
