package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Moves a minion's visual entity incrementally toward a target
 * location each tick, using a simple straight-line stepping approach
 * bounded by the configured max pathfinding nodes rather than a full
 * A* implementation - sufficient for short-range minion movement
 * within a small work radius.
 */
public final class MinionPathfinder {

    private static final double STEP_DISTANCE = 0.25;
    private static final double ARRIVAL_THRESHOLD = 0.6;

    private final MinionsConfig minionsConfig;

    /**
     * Creates a pathfinder.
     *
     * @param minionsConfig resolved minions.yml configuration (max node budget)
     */
    public MinionPathfinder(MinionsConfig minionsConfig) {
        this.minionsConfig = minionsConfig;
    }

    /**
     * Whether the entity has effectively arrived at the target location.
     *
     * @param current the entity's current location
     * @param target  the destination location
     * @return {@code true} if within the arrival threshold distance
     */
    public boolean hasArrived(Location current, Location target) {
        return current.getWorld() != null
                && current.getWorld().equals(target.getWorld())
                && current.distanceSquared(target) <= (ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD);
    }

    /**
     * Advances an entity one step toward a target location. Capped by
     * {@code minions.yml ai.pathfinding-max-nodes} across the entity's
     * lifetime is enforced by the caller (this method performs one
     * step per call, so callers budget calls accordingly); this method
     * itself always performs a single bounded step.
     *
     * @param entity the minion's visual entity to move
     * @param target the destination location
     */
    public void stepToward(Entity entity, Location target) {
        Location current = entity.getLocation();
        if (current.getWorld() == null || !current.getWorld().equals(target.getWorld())) {
            entity.teleport(target);
            return;
        }

        if (hasArrived(current, target)) {
            return;
        }

        org.bukkit.util.Vector direction = target.toVector().subtract(current.toVector());
        double distance = direction.length();
        if (distance <= STEP_DISTANCE) {
            entity.teleport(target);
            return;
        }

        org.bukkit.util.Vector step = direction.normalize().multiply(STEP_DISTANCE);
        Location next = current.clone().add(step);
        next.setDirection(direction);
        entity.teleport(next);
    }

    /**
     * The maximum number of pathfinding steps a single movement
     * attempt should budget before giving up and considering the
     * target unreachable, from {@code minions.yml}.
     *
     * @return the configured max node budget
     */
    public int getMaxNodes() {
        return minionsConfig.getPathfindingMaxNodes();
    }
}