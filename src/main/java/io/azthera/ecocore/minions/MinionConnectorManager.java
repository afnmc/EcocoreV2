package io.azthera.ecocore.minions;

import io.azthera.ecocore.database.dao.MinionConnectionDao;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class MinionConnectorManager {

    public static final double MAX_CONNECTION_DISTANCE = 32.0;

    private static final Set<MinionType> VALID_DESTINATION_TYPES =
            EnumSet.of(MinionType.COLLECTOR, MinionType.MINION_CHEST, MinionType.SELLER);

    private final Logger logger;
    private final MinionConnectionDao connectionDao;

    private final Map<Long, List<Long>> outgoing = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSourceSelection = new ConcurrentHashMap<>();

    public MinionConnectorManager(Logger logger, MinionConnectionDao connectionDao) {
        this.logger = logger;
        this.connectionDao = connectionDao;
    }

    public void loadAll() {
        try {
            List<MinionConnectionDao.Connection_> all = connectionDao.findAll();
            for (MinionConnectionDao.Connection_ connection : all) {
                outgoing.computeIfAbsent(connection.sourceId(), id -> new CopyOnWriteArrayList<>())
                        .add(connection.destinationId());
            }
            logger.info("[EcoCore] Loaded " + all.size() + " minion connector network edges");
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load minion connections: " + exception.getMessage());
        }
    }

    public void beginSelection(UUID playerUuid, long minionId) {
        pendingSourceSelection.put(playerUuid, minionId);
    }

    public Long getPendingSource(UUID playerUuid) {
        return pendingSourceSelection.get(playerUuid);
    }

    public void clearSelection(UUID playerUuid) {
        pendingSourceSelection.remove(playerUuid);
    }

    /**
     * Checks whether a proposed connection is allowed, without saving
     * anything: same owner, destination type is Collector/Minion
     * Chest/Seller only (never another producer), same world, and
     * within {@link #MAX_CONNECTION_DISTANCE} blocks.
     *
     * @param source      the proposed source minion
     * @param destination the proposed destination minion
     * @return {@code null} if valid, or a player-facing rejection reason
     */
    public String validateConnection(MinionData source, MinionData destination) {
        if (source.getId() == destination.getId()) {
            return "Gak bisa connect minion ke dirinya sendiri.";
        }
        if (!source.getOwnerUuid().equals(destination.getOwnerUuid())) {
            return "Kedua minion harus punya pemilik yang sama - gak bisa connect ke minion punya player lain.";
        }
        if (!VALID_DESTINATION_TYPES.contains(destination.getType())) {
            return "§f" + destination.getType().configKey() + " §cbukan tujuan yang valid. Tujuan cuma boleh "
                    + "Collector, Minion Chest, atau Seller Minion.";
        }
        if (!source.getWorld().equals(destination.getWorld())) {
            return "Kedua minion harus di world yang sama.";
        }

        double dx = source.getX() - destination.getX();
        double dy = source.getY() - destination.getY();
        double dz = source.getZ() - destination.getZ();
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (distance > MAX_CONNECTION_DISTANCE) {
            return "Terlalu jauh (" + Math.round(distance) + " block). Maksimal "
                    + (int) MAX_CONNECTION_DISTANCE + " block.";
        }

        return null;
    }

    public boolean connect(UUID ownerUuid, long sourceId, long destinationId) {
        if (sourceId == destinationId) {
            return false;
        }
        if (getOutgoing(sourceId).contains(destinationId)) {
            return true;
        }
        try {
            connectionDao.insert(ownerUuid, sourceId, destinationId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to save minion connection " + sourceId + " -> "
                    + destinationId + ": " + exception.getMessage());
            return false;
        }
        outgoing.computeIfAbsent(sourceId, id -> new CopyOnWriteArrayList<>()).add(destinationId);
        return true;
    }

    public void disconnect(long sourceId, long destinationId) {
        try {
            connectionDao.delete(sourceId, destinationId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to remove minion connection " + sourceId + " -> "
                    + destinationId + ": " + exception.getMessage());
        }
        List<Long> list = outgoing.get(sourceId);
        if (list != null) {
            list.remove(destinationId);
        }
    }

    public void removeAllInvolving(long minionId) {
        try {
            connectionDao.deleteAllInvolving(minionId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to clean up connections for minion "
                    + minionId + ": " + exception.getMessage());
        }
        outgoing.remove(minionId);
        for (List<Long> destinations : outgoing.values()) {
            destinations.remove(minionId);
        }
    }

    public List<Long> getOutgoing(long sourceId) {
        return outgoing.getOrDefault(sourceId, List.of());
    }

    public List<Long> listConnectionsFrom(long sourceId) {
        return new ArrayList<>(getOutgoing(sourceId));
    }
    }
