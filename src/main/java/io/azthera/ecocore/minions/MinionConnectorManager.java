package io.azthera.ecocore.minions;

import io.azthera.ecocore.database.dao.MinionConnectionDao;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Owns the Connector Network: directed links between a player's own
 * minions, drawn with the Connector Tool (see
 * {@code listener.ConnectorToolListener}). Replaces the old
 * fixed-direction Collector routing - a minion can have several
 * outgoing connections (branching to more than one downstream
 * minion) and several incoming ones (several producers feeding one
 * consumer, e.g. two Collectors both feeding one Sell Minion).
 *
 * <p>Every mutating call here writes straight through to
 * {@link MinionConnectionDao} and updates the in-memory cache in the
 * same call, so the cache is always consistent with the database
 * without needing a separate save pass.
 */
public final class MinionConnectorManager {

    private final Logger logger;
    private final MinionConnectionDao connectionDao;

    /** source minion id -> destination minion ids it pushes into. */
    private final Map<Long, List<Long>> outgoing = new ConcurrentHashMap<>();

    /**
     * Per-player in-progress Connector Tool selection: the source
     * minion id they picked with their first click, waiting on a
     * second click to pick the destination. Cleared once a
     * destination is picked (handed off to the confirm screen) or the
     * player picks a new source, overwriting any stale pending pick.
     */
    private final Map<UUID, Long> pendingSourceSelection = new ConcurrentHashMap<>();

    /**
     * Creates the connector manager.
     *
     * @param logger        plugin logger
     * @param connectionDao DAO backing persistent connections
     */
    public MinionConnectorManager(Logger logger, MinionConnectionDao connectionDao) {
        this.logger = logger;
        this.connectionDao = connectionDao;
    }

    /**
     * Loads every persisted connection into the in-memory cache. Call
     * once at startup, after {@code MinionManager.loadAll()}.
     */
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

    /**
     * Records the first click of a Connector Tool use: the source
     * minion for a new connection about to be drawn.
     *
     * @param playerUuid the drawing player
     * @param minionId   the selected source minion's database id
     */
    public void beginSelection(UUID playerUuid, long minionId) {
        pendingSourceSelection.put(playerUuid, minionId);
    }

    /**
     * The source minion id a player currently has pending from a
     * first Connector Tool click, if any.
     *
     * @param playerUuid the player to check
     * @return the pending source minion id, or {@code null} if none
     */
    public Long getPendingSource(UUID playerUuid) {
        return pendingSourceSelection.get(playerUuid);
    }

    /**
     * Clears a player's pending Connector Tool selection (cancel, or
     * handed off to the confirm screen).
     *
     * @param playerUuid the player to clear
     */
    public void clearSelection(UUID playerUuid) {
        pendingSourceSelection.remove(playerUuid);
    }

    /**
     * Persists a new connection and adds it to the in-memory cache.
     * A source may already have other outgoing connections (branching
     * is allowed); duplicate source/destination pairs are simply
     * ignored rather than erroring.
     *
     * @param ownerUuid     the connection's owner
     * @param sourceId      the source minion's database id
     * @param destinationId the destination minion's database id
     * @return {@code true} if the connection was saved (or already existed)
     */
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

    /**
     * Removes a specific connection, both from persistence and the
     * in-memory cache.
     *
     * @param sourceId      the source minion's database id
     * @param destinationId the destination minion's database id
     */
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

    /**
     * Removes every connection touching a minion (as either source or
     * destination), called when that minion is removed entirely.
     *
     * @param minionId the removed minion's database id
     */
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

    /**
     * The destination minion ids a minion currently pushes into.
     *
     * @param sourceId the source minion's database id
     * @return the connected destination ids, empty if none
     */
    public List<Long> getOutgoing(long sourceId) {
        return outgoing.getOrDefault(sourceId, List.of());
    }

    /**
     * Every connection, source and destination pairs, for a given
     * source minion - used by the Connector GUI to list active
     * connections for display/removal.
     *
     * @param sourceId the source minion's database id
     * @return a snapshot list of destination ids connected from this source
     */
    public List<Long> listConnectionsFrom(long sourceId) {
        return new ArrayList<>(getOutgoing(sourceId));
    }
}
