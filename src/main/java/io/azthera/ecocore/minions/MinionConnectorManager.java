package io.azthera.ecocore.minions;

import io.azthera.ecocore.database.dao.MinionConnectionDao;
import io.azthera.ecocore.model.MinionData;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class MinionConnectorManager {

    public static final double DIRECT_MAX_DISTANCE = 10.0;

    private final Logger logger;
    private final MinionConnectionDao connectionDao;
    private final MinionConnectorEntityManager connectorEntityManager;

    private record Edge(long destinationId, MinionConnectionDao.LinkMode linkMode, Long relayConnectorId) {
    }

    private final Map<Long, List<Edge>> outgoing = new ConcurrentHashMap<>();

    public MinionConnectorManager(Logger logger, MinionConnectionDao connectionDao,
                                   MinionConnectorEntityManager connectorEntityManager) {
        this.logger = logger;
        this.connectionDao = connectionDao;
        this.connectorEntityManager = connectorEntityManager;
    }

    public void loadAll() {
        try {
            List<MinionConnectionDao.Connection_> all = connectionDao.findAll();
            for (MinionConnectionDao.Connection_ connection : all) {
                outgoing.computeIfAbsent(connection.sourceId(), id -> new CopyOnWriteArrayList<>())
                        .add(new Edge(connection.destinationId(), connection.linkMode(), connection.relayConnectorId()));
            }
            logger.info("[EcoCore] Loaded " + all.size() + " minion connector network edges");
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load minion connections: " + exception.getMessage());
        }
    }

    public record ResolvedLink(MinionConnectionDao.LinkMode linkMode, Long relayConnectorId,
                                double maxDistance, String rejectionReason) {
        public boolean isValid() {
            return rejectionReason == null;
        }
    }

    public ResolvedLink resolveLink(MinionData source, MinionData destination) {
        if (source.getId() == destination.getId()) {
            return new ResolvedLink(null, null, 0, "Gak bisa connect minion ke dirinya sendiri.");
        }
        if (!source.getOwnerUuid().equals(destination.getOwnerUuid())) {
            return new ResolvedLink(null, null, 0,
                    "Kedua minion harus punya pemilik yang sama - gak bisa connect ke minion punya player lain.");
        }
        if (!source.getWorld().equals(destination.getWorld())) {
            return new ResolvedLink(null, null, 0, "Kedua minion harus di world yang sama.");
        }

        org.bukkit.Location sourceLoc = new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(source.getWorld()), source.getX(), source.getY(), source.getZ());
        org.bukkit.Location destinationLoc = new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(destination.getWorld()), destination.getX(), destination.getY(), destination.getZ());

        MinionConnectorEntityManager.ActiveConnector connectorAtSource = connectorEntityManager.findConnectorNear(sourceLoc);
        MinionConnectorEntityManager.ActiveConnector connectorAtDestination = connectorEntityManager.findConnectorNear(destinationLoc);
        MinionConnectorEntityManager.ActiveConnector relayConnector = connectorAtSource != null ? connectorAtSource : connectorAtDestination;

        double distance = distanceBetween(source, destination);

        if (relayConnector != null) {
            double maxRelayDistance = connectorEntityManager.getMaxRelayDistance(relayConnector);
            if (distance > maxRelayDistance) {
                return new ResolvedLink(null, null, maxRelayDistance,
                        "Terlalu jauh buat relay ini (" + Math.round(distance) + " block). Maksimal "
                                + (int) maxRelayDistance + " block. Upgrade range connector ini biar lebih jauh.");
            }
            return new ResolvedLink(MinionConnectionDao.LinkMode.RELAY, relayConnector.getId(), maxRelayDistance, null);
        }

        if (distance > DIRECT_MAX_DISTANCE) {
            return new ResolvedLink(null, null, DIRECT_MAX_DISTANCE,
                    "Terlalu jauh buat direct connection (" + Math.round(distance) + " block). Maksimal "
                            + (int) DIRECT_MAX_DISTANCE + " block tanpa Minion Connector. "
                            + "Taruh Minion Connector di dekat salah satu minion buat konek lebih jauh.");
        }
        return new ResolvedLink(MinionConnectionDao.LinkMode.DIRECT, null, DIRECT_MAX_DISTANCE, null);
    }

    private double distanceBetween(MinionData source, MinionData destination) {
        double dx = source.getX() - destination.getX();
        double dy = source.getY() - destination.getY();
        double dz = source.getZ() - destination.getZ();
        return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    public boolean connect(UUID ownerUuid, long sourceId, long destinationId, ResolvedLink resolvedLink) {
        if (sourceId == destinationId || !resolvedLink.isValid()) {
            return false;
        }
        if (getOutgoing(sourceId).stream().anyMatch(edge -> edge.destinationId() == destinationId)) {
            return true;
        }
        try {
            connectionDao.insert(ownerUuid, sourceId, destinationId, resolvedLink.linkMode(), resolvedLink.relayConnectorId());
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to save minion connection " + sourceId + " -> "
                    + destinationId + ": " + exception.getMessage());
            return false;
        }
        outgoing.computeIfAbsent(sourceId, id -> new CopyOnWriteArrayList<>())
                .add(new Edge(destinationId, resolvedLink.linkMode(), resolvedLink.relayConnectorId()));
        return true;
    }

    public void disconnect(long sourceId, long destinationId) {
        try {
            connectionDao.delete(sourceId, destinationId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to remove minion connection " + sourceId + " -> "
                    + destinationId + ": " + exception.getMessage());
        }
        List<Edge> list = outgoing.get(sourceId);
        if (list != null) {
            list.removeIf(edge -> edge.destinationId() == destinationId);
        }
    }

    public void removeAllInvolving(long minionId) {
        try {
            connectionDao.deleteAllInvolving(minionId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to clean up connections for minion " + minionId + ": " + exception.getMessage());
        }
        outgoing.remove(minionId);
        for (List<Edge> destinations : outgoing.values()) {
            destinations.removeIf(edge -> edge.destinationId() == minionId);
        }
    }

    public void removeAllUsingRelay(long connectorId) {
        try {
            connectionDao.deleteAllUsingRelay(connectorId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to clean up relay connections for connector " + connectorId + ": " + exception.getMessage());
        }
        for (List<Edge> destinations : outgoing.values()) {
            destinations.removeIf(edge -> connectorId == (edge.relayConnectorId() != null ? edge.relayConnectorId() : -1L));
        }
    }

    public List<Long> getOutgoingIds(long sourceId) {
        List<Long> results = new ArrayList<>();
        for (Edge edge : getOutgoing(sourceId)) {
            results.add(edge.destinationId());
        }
        return results;
    }

    /**
     * Whether a direct edge already exists from {@code sourceId} to
     * {@code destinationId} - used by the connect-list GUI to show
     * "sudah terhubung" instead of letting the player attempt a
     * duplicate connection.
     *
     * @param sourceId the source minion's database id
     * @param destinationId the destination minion's database id
     * @return {@code true} if this exact source-to-destination edge already exists
     */
    public boolean isConnected(long sourceId, long destinationId) {
        return getOutgoing(sourceId).stream().anyMatch(edge -> edge.destinationId() == destinationId);
    }

    private List<Edge> getOutgoing(long sourceId) {
        return outgoing.getOrDefault(sourceId, List.of());
    }

    public List<Long> listConnectionsFrom(long sourceId) {
        return getOutgoingIds(sourceId);
    }
}
