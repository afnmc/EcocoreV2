// FILE: src/main/java/io/azthera/ecocore/database/dao/MinionConnectorEntityDao.java
package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code minion_connector_entities} table
 * (Revisi 9): standalone placeable relay entities that are NOT
 * minions, used to extend a connection's max distance beyond the
 * fixed 10-block direct range.
 */
public final class MinionConnectorEntityDao {

    /**
     * A single persisted connector entity.
     *
     * @param id the connector's own database id
     * @param ownerUuid the owning player
     * @param world the world the connector is placed in
     * @param x x coordinate
     * @param y y coordinate
     * @param z z coordinate
     * @param rangeLevel the current upgrade tier of this connector's range
     * @param entityUuid the tagged visual entity uuid, may be {@code null} if never spawned
     */
    public record ConnectorEntityRecord(long id, UUID ownerUuid, String world,
                                         double x, double y, double z,
                                         int rangeLevel, UUID entityUuid) {
    }

    private final DatabaseManager databaseManager;

    public MinionConnectorEntityDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Inserts a newly placed connector entity.
     *
     * @param ownerUuid the placing player's uuid
     * @param world the world name
     * @param x x coordinate
     * @param y y coordinate
     * @param z z coordinate
     * @return the generated row id
     * @throws SQLException if the insert fails
     */
    public long insert(UUID ownerUuid, String world, double x, double y, double z) throws SQLException {
        String sql = """
            INSERT INTO minion_connector_entities
            (owner_uuid, world, x, y, z, range_level, entity_uuid, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 0, NULL, ?, ?)
            """;
        long now = System.currentTimeMillis();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, world);
            statement.setDouble(3, x);
            statement.setDouble(4, y);
            statement.setDouble(5, z);
            statement.setLong(6, now);
            statement.setLong(7, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public void updateEntityUuid(long id, UUID entityUuid) throws SQLException {
        String sql = "UPDATE minion_connector_entities SET entity_uuid = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityUuid != null ? entityUuid.toString() : null);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updateRangeLevel(long id, int rangeLevel) throws SQLException {
        String sql = "UPDATE minion_connector_entities SET range_level = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rangeLevel);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM minion_connector_entities WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    public List<ConnectorEntityRecord> findAll() throws SQLException {
        String sql = "SELECT * FROM minion_connector_entities";
        List<ConnectorEntityRecord> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(mapRow(resultSet));
            }
        }
        return results;
    }

    private ConnectorEntityRecord mapRow(ResultSet resultSet) throws SQLException {
        String entityUuidRaw = resultSet.getString("entity_uuid");
        return new ConnectorEntityRecord(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getInt("range_level"),
                entityUuidRaw != null ? UUID.fromString(entityUuidRaw) : null
        );
    }
}