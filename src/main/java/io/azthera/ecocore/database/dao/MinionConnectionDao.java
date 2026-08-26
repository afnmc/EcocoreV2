// FILE: src/main/java/io/azthera/ecocore/database/dao/MinionConnectionDao.java
package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code minion_link_connections} table
 * (Revisi 9): the persisted edges of the rebuilt Connector system.
 * Each row is one directed connection (a source minion pushes items
 * into a destination minion), tagged with whether it's a DIRECT link
 * (max 10 blocks, free, fixed) or a RELAY link (routed through a
 * {@code MinionConnectorEntity}, upgradeable range).
 */
public final class MinionConnectionDao {

    public enum LinkMode {
        DIRECT,
        RELAY
    }

    /**
     * A single persisted directed connection.
     *
     * @param id the connection's own database id
     * @param ownerUuid the connection's owner (must own both minions)
     * @param sourceId the source minion's database id
     * @param destinationId the destination minion's database id
     * @param linkMode DIRECT or RELAY
     * @param relayConnectorId the relay connector entity's id, or {@code null} for DIRECT links
     */
    public record Connection_(long id, UUID ownerUuid, long sourceId, long destinationId,
                               LinkMode linkMode, Long relayConnectorId) {
    }

    private final DatabaseManager databaseManager;

    public MinionConnectionDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Loads every persisted connection, for populating the in-memory
     * network cache at startup.
     *
     * @return every connection currently stored
     * @throws SQLException if the query fails
     */
    public List<Connection_> findAll() throws SQLException {
        String sql = "SELECT id, owner_uuid, source_minion_id, destination_minion_id, link_mode, relay_connector_id "
                + "FROM minion_link_connections";
        List<Connection_> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(mapRow(resultSet));
            }
        }
        return results;
    }

    /**
     * Persists a new connection. Silently does nothing if the same
     * source/destination pair already exists (the table's UNIQUE
     * constraint), since re-confirming an existing route isn't an error.
     *
     * @param ownerUuid the connection's owner
     * @param sourceId the source minion's database id
     * @param destinationId the destination minion's database id
     * @param linkMode DIRECT or RELAY
     * @param relayConnectorId the relay connector entity's id, or {@code null} for DIRECT
     * @return the generated row id, or {@code -1} if it already existed
     * @throws SQLException if the insert fails for a reason other than a duplicate
     */
    public long insert(UUID ownerUuid, long sourceId, long destinationId,
                        LinkMode linkMode, Long relayConnectorId) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO minion_link_connections
            (owner_uuid, source_minion_id, destination_minion_id, link_mode, relay_connector_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerUuid.toString());
            statement.setLong(2, sourceId);
            statement.setLong(3, destinationId);
            statement.setString(4, linkMode.name());
            if (relayConnectorId != null) {
                statement.setLong(5, relayConnectorId);
            } else {
                statement.setNull(5, java.sql.Types.INTEGER);
            }
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /**
     * Deletes a specific connection.
     *
     * @param sourceId the source minion's database id
     * @param destinationId the destination minion's database id
     * @throws SQLException if the delete fails
     */
    public void delete(long sourceId, long destinationId) throws SQLException {
        String sql = "DELETE FROM minion_link_connections WHERE source_minion_id = ? AND destination_minion_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceId);
            statement.setLong(2, destinationId);
            statement.executeUpdate();
        }
    }

    /**
     * Deletes every connection touching a minion (as either source or
     * destination), used when that minion is removed entirely.
     *
     * @param minionId the removed minion's database id
     * @throws SQLException if the delete fails
     */
    public void deleteAllInvolving(long minionId) throws SQLException {
        String sql = "DELETE FROM minion_link_connections WHERE source_minion_id = ? OR destination_minion_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, minionId);
            statement.setLong(2, minionId);
            statement.executeUpdate();
        }
    }

    /**
     * Deletes every RELAY connection routed through a given connector
     * entity, used when that connector entity is removed.
     *
     * @param connectorId the removed connector entity's database id
     * @throws SQLException if the delete fails
     */
    public void deleteAllUsingRelay(long connectorId) throws SQLException {
        String sql = "DELETE FROM minion_link_connections WHERE relay_connector_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, connectorId);
            statement.executeUpdate();
        }
    }

    private Connection_ mapRow(ResultSet resultSet) throws SQLException {
        long relayId = resultSet.getLong("relay_connector_id");
        Long relayConnectorId = resultSet.wasNull() ? null : relayId;
        return new Connection_(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getLong("source_minion_id"),
                resultSet.getLong("destination_minion_id"),
                LinkMode.valueOf(resultSet.getString("link_mode")),
                relayConnectorId
        );
    }
}