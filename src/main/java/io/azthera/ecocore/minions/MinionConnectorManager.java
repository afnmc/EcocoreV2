package io.azthera.ecocore.minions;

import io.azthera.ecocore.database.dao.MinionConnectionDao;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class MinionConnectorManager {

    public record ConnectionView(long destinationId, MinionConnectionDao.LinkMode linkMode, Long relayConnectorId) {
    }

    private record Edge(long sourceId, long destinationId, MinionConnectionDao.LinkMode linkMode, Long relayConnectorId) {
    }

    private final Logger logger;
    private final MinionConnectionDao connectionDao;
    private final MinionConnectorEntityManager connectorEntityManager;
    private final int maxDirectDistance;
    private final int maxRelayDistanceCap;
    private final boolean debug;

    private final Map<Long, List<Edge>> outgoing = new ConcurrentHashMap<>();
    private final Map<Long, List<Edge>> relayIndex = new ConcurrentHashMap<>();

    public MinionConnectorManager(Logger logger, MinionConnectionDao connectionDao,
                                   MinionConnectorEntityManager connectorEntityManager,
                                   int maxDirectDistance, int maxRelayDistanceCap, boolean debug) {
        this.logger = logger;
        this.connectionDao = connectionDao;
        this.connectorEntityManager = connectorEntityManager;
        this.maxDirectDistance = maxDirectDistance;
        this.maxRelayDistanceCap = maxRelayDistanceCap;
        this.debug = debug;
    }

    public void loadAll() {
        try {
            List<MinionConnectionDao.Connection_> all = connectionDao.findAll();
            for (MinionConnectionDao.Connection_ connection : all) {
                Edge edge = new Edge(connection.sourceId(), connection.destinationId(),
                        connection.linkMode(), connection.relayConnectorId());
                outgoing.computeIfAbsent(connection.sourceId(), id -> new CopyOnWriteArrayList<>()).add(edge);
                if (edge.linkMode() == MinionConnectionDao.LinkMode.RELAY && edge.relayConnectorId() != null) {
                    relayIndex.computeIfAbsent(edge.relayConnectorId(), id -> new CopyOnWriteArrayList<>()).add(edge);
                }
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
            return new ResolvedLink(null, null, 0, "Kedua minion harus punya pemilik yang sama.");
        }
        if (!source.getWorld().equals(destination.getWorld())) {
            return new ResolvedLink(null, null, 0, "Kedua minion harus di world yang sama.");
        }
        
        Location sourceLoc = toLocation(source);
        Location destinationLoc = toLocation(destination);
        if (sourceLoc == null || destinationLoc == null) return new ResolvedLink(null, null, 0, "World tidak ditemukan.");

        double distance = distanceBetween(source, destination);

        // 1. Try RELAY
        MinionConnectorEntityManager.ActiveConnector bestRelay = null;
        double bestRange = -1.0;
        for (MinionConnectorEntityManager.ActiveConnector candidate : connectorEntityManager.findConnectorsNear(sourceLoc, destinationLoc)) {
            double range = effectiveRelayRange(candidate);
            if (distanceSquaredTo(candidate, sourceLoc) > range * range) continue;
            if (distanceSquaredTo(candidate, destinationLoc) > range * range) continue;
            if (range > bestRange) {
                bestRange = range;
                bestRelay = candidate;
            }
        }
        
        if (bestRelay != null) {
            return new ResolvedLink(MinionConnectionDao.LinkMode.RELAY, bestRelay.getId(), bestRange, null);
        }

        // 2. Try DIRECT
        if (distance > maxDirectDistance) {
            return new ResolvedLink(null, null, maxDirectDistance,
                    "Terlalu jauh (" + Math.round(distance) + " block). Maksimal " + maxDirectDistance + " block tanpa Connector.");
        }
        return new ResolvedLink(MinionConnectionDao.LinkMode.DIRECT, null, maxDirectDistance, null);
    }

    public boolean isRelayStillValid(MinionData source, MinionData destination, MinionConnectorEntityManager.ActiveConnector relay) {
        Location relayLoc = relay.toLocation();
        Location sourceLoc = toLocation(source);
        Location destinationLoc = toLocation(destination);
        if (relayLoc == null || sourceLoc == null || destinationLoc == null) return false;
        double range = effectiveRelayRange(relay);
        double rangeSq = range * range;
        return sourceLoc.distanceSquared(relayLoc) <= rangeSq && destinationLoc.distanceSquared(relayLoc) <= rangeSq;
    }

    private double effectiveRelayRange(MinionConnectorEntityManager.ActiveConnector connector) {
        return Math.min(connectorEntityManager.getMaxRelayDistance(connector), maxRelayDistanceCap);
    }

    private double distanceSquaredTo(MinionConnectorEntityManager.ActiveConnector connector, Location location) {
        double dx = connector.getX() - location.getX();
        double dy = connector.getY() - location.getY();
        double dz = connector.getZ() - location.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private Location toLocation(MinionData data) {
        var world = Bukkit.getWorld(data.getWorld());
        return world != null ? new Location(world, data.getX(), data.getY(), data.getZ()) : null;
    }

    private double distanceBetween(MinionData source, MinionData destination) {
        double dx = source.getX() - destination.getX();
        double dy = source.getY() - destination.getY();
        double dz = source.getZ() - destination.getZ();
        return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    public boolean connect(UUID ownerUuid, long sourceId, long destinationId, ResolvedLink resolvedLink) {
        if (sourceId == destinationId || !resolvedLink.isValid()) return false;
        if (isConnected(sourceId, destinationId)) return true;
        try {
            connectionDao.insert(ownerUuid, sourceId, destinationId, resolvedLink.linkMode(), resolvedLink.relayConnectorId());
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to save connection: " + exception.getMessage());
            return false;
        }
        Edge edge = new Edge(sourceId, destinationId, resolvedLink.linkMode(), resolvedLink.relayConnectorId());
        outgoing.computeIfAbsent(sourceId, id -> new CopyOnWriteArrayList<>()).add(edge);
        if (edge.linkMode() == MinionConnectionDao.LinkMode.RELAY && edge.relayConnectorId() != null) {
            relayIndex.computeIfAbsent(edge.relayConnectorId(), id -> new CopyOnWriteArrayList<>()).add(edge);
        }
        return true;
    }

    public void disconnect(long sourceId, long destinationId) {
        try { connectionDao.delete(sourceId, destinationId); } catch (SQLException e) { logger.severe("DB Error"); }
        List<Edge> list = outgoing.get(sourceId);
        if (list != null) list.removeIf(edge -> edge.destinationId() == destinationId);
        for (List<Edge> edges : relayIndex.values()) edges.removeIf(edge -> edge.sourceId() == sourceId && edge.destinationId() == destinationId);
    }

    public void removeAllInvolving(long minionId) {
        try { connectionDao.deleteAllInvolving(minionId); } catch (SQLException e) { logger.severe("DB Error"); }
        outgoing.remove(minionId);
        for (List<Edge> edges : outgoing.values()) edges.removeIf(edge -> edge.destinationId() == minionId);
        for (List<Edge> edges : relayIndex.values()) edges.removeIf(edge -> edge.sourceId() == minionId || edge.destinationId() == minionId);
    }

    public void removeAllUsingRelay(long connectorId) {
        try { connectionDao.deleteAllUsingRelay(connectorId); } catch (SQLException e) { logger.severe("DB Error"); }
        relayIndex.remove(connectorId);
        for (List<Edge> edges : outgoing.values()) {
            edges.removeIf(edge -> edge.linkMode() == MinionConnectionDao.LinkMode.RELAY && Objects.equals(edge.relayConnectorId(), connectorId));
        }
    }

    public List<ConnectionView> getOutgoingConnections(long sourceId) {
        List<ConnectionView> results = new ArrayList<>();
        for (Edge edge : outgoing.getOrDefault(sourceId, List.of())) {
            results.add(new ConnectionView(edge.destinationId(), edge.linkMode(), edge.relayConnectorId()));
        }
        return results;
    }

    public List<Long> getOutgoingIds(long sourceId) {
        return getOutgoingConnections(sourceId).stream().map(ConnectionView::destinationId).toList();
    }

    public List<ConnectionView> getConnectionsUsingRelay(long relayId) {
        List<ConnectionView> results = new ArrayList<>();
        for (Edge edge : relayIndex.getOrDefault(relayId, List.of())) {
            results.add(new ConnectionView(edge.destinationId(), edge.linkMode(), edge.relayConnectorId()));
        }
        return results;
    }

    public boolean isConnected(long sourceId, long destinationId) {
        return outgoing.getOrDefault(sourceId, List.of()).stream().anyMatch(edge -> edge.destinationId() == destinationId);
    }
}