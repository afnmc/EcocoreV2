// FILE: src/main/java/io/azthera/ecocore/minions/MinionConnectorEntityManager.java
package io.azthera.ecocore.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.database.dao.MinionConnectorEntityDao;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages {@code MinionConnectorEntity} instances (Revisi 9): standalone
 * placeable relay points that are NOT minions themselves, used to extend
 * a connection's reachable distance beyond the fixed 10-block direct
 * range. Visually represented as a small floating ArmorStand marker.
 */
public final class MinionConnectorEntityManager {

    private static final String CONNECTOR_ID_KEY = "minion_connector_id";

    private final Logger logger;
    private final MinionConnectorEntityDao connectorEntityDao;
    private final MinionsConfig minionsConfig;

    /**
     * A live, in-memory connector entity.
     */
    public static final class ActiveConnector {
        private final long id;
        private final UUID ownerUuid;
        private String world;
        private double x;
        private double y;
        private double z;
        private int rangeLevel;
        private Entity entity;

        private ActiveConnector(long id, UUID ownerUuid, String world, double x, double y, double z,
                                 int rangeLevel, Entity entity) {
            this.id = id;
            this.ownerUuid = ownerUuid;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rangeLevel = rangeLevel;
            this.entity = entity;
        }

        public long getId() {
            return id;
        }

        public UUID getOwnerUuid() {
            return ownerUuid;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public int getRangeLevel() {
            return rangeLevel;
        }

        public Location toLocation() {
            World bukkitWorld = Bukkit.getWorld(world);
            return bukkitWorld != null ? new Location(bukkitWorld, x, y, z) : null;
        }
    }

    private final Map<Long, ActiveConnector> activeConnectors = new ConcurrentHashMap<>();

    public MinionConnectorEntityManager(Logger logger, MinionConnectorEntityDao connectorEntityDao,
                                         MinionsConfig minionsConfig) {
        this.logger = logger;
        this.connectorEntityDao = connectorEntityDao;
        this.minionsConfig = minionsConfig;
    }

    public void loadAll() {
        try {
            List<MinionConnectorEntityDao.ConnectorEntityRecord> all = connectorEntityDao.findAll();
            for (MinionConnectorEntityDao.ConnectorEntityRecord record : all) {
                ActiveConnector active = new ActiveConnector(record.id(), record.ownerUuid(), record.world(),
                        record.x(), record.y(), record.z(), record.rangeLevel(), null);
                activeConnectors.put(record.id(), active);
            }
            logger.info("[EcoCore] Loaded " + activeConnectors.size() + " minion connector entities");
            int reattached = 0;
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    for (Entity entity : chunk.getEntities()) {
                        Long connectorId = resolveConnectorId(entity);
                        if (connectorId != null && activeConnectors.containsKey(connectorId)) {
                            activeConnectors.get(connectorId).entity = entity;
                            reattached++;
                        }
                    }
                }
            }
            if (reattached > 0) {
                logger.info("[EcoCore] Reattached " + reattached + " connector entities from loaded chunks");
            }
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load minion connector entities: " + exception.getMessage());
        }
    }

    public Long resolveConnectorId(Entity entity) {
        if (entity == null) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), CONNECTOR_ID_KEY);
        return entity.getPersistentDataContainer().get(key, PersistentDataType.LONG);
    }

    public void attachOrCleanupEntity(Entity entity) {
        Long connectorId = resolveConnectorId(entity);
        if (connectorId == null) {
            return;
        }
        ActiveConnector active = activeConnectors.get(connectorId);
        if (active == null) {
            entity.remove();
            return;
        }
        active.entity = entity;
    }

    /**
     * Places a new connector entity at the given location.
     *
     * @param player the placing player
     * @param location the placement location
     * @return the newly placed connector, or {@code null} if placement failed
     */
    public ActiveConnector place(Player player, Location location) {
        try {
            long id = connectorEntityDao.insert(player.getUniqueId(),
                    location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
            ActiveConnector active = new ActiveConnector(id, player.getUniqueId(),
                    location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), 0, null);
            Entity entity = spawnVisualEntity(location, id);
            active.entity = entity;
            try {
                connectorEntityDao.updateEntityUuid(id, entity.getUniqueId());
            } catch (SQLException exception) {
                logger.warning("[EcoCore] Failed to persist entity uuid for connector " + id + ": " + exception.getMessage());
            }
            activeConnectors.put(id, active);
            return active;
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to place connector entity: " + exception.getMessage());
            return null;
        }
    }

    private Entity spawnVisualEntity(Location location, long connectorId) {
        Location spawnAt = location.clone().add(0.5, 0.5, 0.5);
        ArmorStand marker = location.getWorld().spawn(spawnAt, ArmorStand.class);
        marker.setInvisible(true);
        marker.setMarker(true);
        marker.setGravity(false);
        marker.setInvulnerable(true);
        marker.setPersistent(true);
        marker.setCustomNameVisible(true);
        marker.setCustomName("§bMinion Connector");
        marker.setSmall(true);
        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), CONNECTOR_ID_KEY);
        marker.getPersistentDataContainer().set(key, PersistentDataType.LONG, connectorId);
        return marker;
    }

    /**
     * Whether a connector entity exists at (or very near, within one
     * block) the given location - used by
     * {@code MinionConnectorManager} to auto-detect direct-vs-relay
     * mode when the player clicks source/destination minions.
     *
     * @param location the location to check
     * @return the connector at that location, or {@code null} if none
     */
    public ActiveConnector findConnectorNear(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        String worldName = location.getWorld().getName();
        for (ActiveConnector connector : activeConnectors.values()) {
            if (!connector.world.equals(worldName)) {
                continue;
            }
            double dx = connector.x - location.getX();
            double dy = connector.y - location.getY();
            double dz = connector.z - location.getZ();
            if ((dx * dx + dy * dy + dz * dz) 4.0) {
                return connector;
            }
        }
        return null;
    }

    public ActiveConnector getConnector(long id) {
        return activeConnectors.get(id);
    }

    /**
     * The max distance a relay connection may span through this
     * connector, based on its current range upgrade level.
     *
     * @param connector the connector entity
     * @return the max relay distance in blocks
     */
    public double getMaxRelayDistance(ActiveConnector connector) {
        return minionsConfig.getConnectorBaseRange()
                + (connector.rangeLevel * minionsConfig.getConnectorRangePerUpgrade());
    }

    public boolean canUpgradeRange(ActiveConnector connector) {
        return connector.rangeLevel .getConnectorMaxRangeUpgrades();
    }

    public double computeUpgradeCost(ActiveConnector connector) {
        return minionsConfig.getConnectorUpgradeBaseCost()
                * Math.pow(minionsConfig.getConnectorUpgradeCostGrowth(), connector.rangeLevel);
    }

    /**
     * Applies a range upgrade to a connector, assuming payment has
     * already been validated/charged by the caller.
     *
     * @param connector the connector to upgrade
     */
    public void applyRangeUpgrade(ActiveConnector connector) {
        connector.rangeLevel++;
        try {
            connectorEntityDao.updateRangeLevel(connector.id, connector.rangeLevel);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to persist range upgrade for connector " + connector.id + ": " + exception.getMessage());
        }
    }

    public boolean remove(long connectorId) {
        ActiveConnector active = activeConnectors.remove(connectorId);
        if (active == null) {
            return false;
        }
        if (active.entity != null && active.entity.isValid()) {
            active.entity.remove();
        }
        try {
            connectorEntityDao.delete(connectorId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to delete connector " + connectorId + ": " + exception.getMessage());
        }
        return true;
    }

    public List<ActiveConnector> getConnectorsOwnedBy(UUID ownerUuid) {
        List<ActiveConnector> results = new ArrayList<>();
        for (ActiveConnector connector : activeConnectors.values()) {
            if (connector.ownerUuid.equals(ownerUuid)) {
                results.add(connector);
            }
        }
        return results;
    }
}