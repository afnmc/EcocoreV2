package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code minions_data} table.
 * Inventory contents are persisted separately as a serialized JSON
 * blob ({@code storage_json}); this DAO treats it as an opaque string.
 */
public final class MinionsDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a minions DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public MinionsDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Inserts a newly placed minion and returns its generated database id.
     *
     * @param data        the minion data to persist (its id field is ignored)
     * @param storageJson serialized inventory contents, may be {@code null} for empty storage
     * @return the generated row id
     * @throws SQLException if the insert fails
     */
    public long insert(MinionData data, String storageJson) throws SQLException {
        String sql = """
                INSERT INTO minions_data
                    (owner_uuid, type, level, xp, energy, fuel_ticks_remaining, world, x, y, z,
                     storage_slots, radius, speed_ticks, storage_json, auto_repair, auto_sell,
                     auto_smelt, entity_uuid, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindInsertOrUpdate(statement, data, storageJson);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /**
     * Updates an existing minion's full persistent state.
     *
     * @param data        the minion data to persist, must have a valid {@link MinionData#getId()}
     * @param storageJson serialized inventory contents, may be {@code null} for empty storage
     * @throws SQLException if the update fails
     */
    public void update(MinionData data, String storageJson) throws SQLException {
        String sql = """
                UPDATE minions_data SET
                    level = ?, xp = ?, energy = ?, fuel_ticks_remaining = ?, world = ?, x = ?, y = ?, z = ?,
                    storage_slots = ?, radius = ?, speed_ticks = ?, storage_json = ?,
                    auto_repair = ?, auto_sell = ?, auto_smelt = ?, entity_uuid = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, data.getLevel());
            statement.setLong(2, data.getXp());
            statement.setInt(3, data.getEnergy());
            statement.setInt(4, data.getFuelTicksRemaining());
            statement.setString(5, data.getWorld());
            statement.setDouble(6, data.getX());
            statement.setDouble(7, data.getY());
            statement.setDouble(8, data.getZ());
            statement.setInt(9, data.getStorageSlots());
            statement.setInt(10, data.getRadius());
            statement.setInt(11, data.getSpeedTicks());
            statement.setString(12, storageJson);
            statement.setBoolean(13, data.isAutoRepair());
            statement.setBoolean(14, data.isAutoSell());
            statement.setBoolean(15, data.isAutoSmelt());
            statement.setString(16, data.getEntityUuid() != null ? data.getEntityUuid().toString() : null);
            statement.setLong(17, data.getUpdatedAt());
            statement.setLong(18, data.getId());
            statement.executeUpdate();
        }
    }

    /**
     * Persists just the entity uuid for a minion, called right after
     * its visual entity is (re)spawned.
     *
     * @param id         the minion's database id
     * @param entityUuid the entity uuid to store, may be {@code null}
     * @throws SQLException if the update fails
     */
    public void updateEntityUuid(long id, UUID entityUuid) throws SQLException {
        String sql = "UPDATE minions_data SET entity_uuid = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityUuid != null ? entityUuid.toString() : null);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    /**
     * Deletes a minion permanently.
     *
     * @param id the minion's database id
     * @throws SQLException if the delete fails
     */
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM minions_data WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    /**
     * Finds a single minion by its database id.
     *
     * @param id the minion's database id
     * @return the minion, or {@code null} if it doesn't exist
     * @throws SQLException if the query fails
     */
    public MinionData findById(long id) throws SQLException {
        String sql = "SELECT * FROM minions_data WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        }
    }

    /**
     * Finds every minion owned by a player.
     *
     * @param ownerUuid the owning player's uuid
     * @return the player's minions
     * @throws SQLException if the query fails
     */
    public List<MinionData> findByOwner(UUID ownerUuid) throws SQLException {
        String sql = "SELECT * FROM minions_data WHERE owner_uuid = ?";
        List<MinionData> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    /**
     * Loads every minion in the database, used at startup by
     * {@code MinionManager} to load minion state (their live entity
     * is re-attached lazily via chunk load events, not spawned here).
     *
     * @return every persisted minion
     * @throws SQLException if the query fails
     */
    public List<MinionData> findAll() throws SQLException {
        String sql = "SELECT * FROM minions_data";
        List<MinionData> results = new ArrayList<>();
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
     * Reads the raw serialized storage contents for a minion.
     *
     * @param id the minion's database id
     * @return the serialized storage JSON, or {@code null} if empty/not found
     * @throws SQLException if the query fails
     */
    public String findStorageJson(long id) throws SQLException {
        String sql = "SELECT storage_json FROM minions_data WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("storage_json") : null;
            }
        }
    }

    private void bindInsertOrUpdate(PreparedStatement statement, MinionData data, String storageJson) throws SQLException {
        statement.setString(1, data.getOwnerUuid().toString());
        statement.setString(2, data.getType().name());
        statement.setInt(3, data.getLevel());
        statement.setLong(4, data.getXp());
        statement.setInt(5, data.getEnergy());
        statement.setInt(6, data.getFuelTicksRemaining());
        statement.setString(7, data.getWorld());
        statement.setDouble(8, data.getX());
        statement.setDouble(9, data.getY());
        statement.setDouble(10, data.getZ());
        statement.setInt(11, data.getStorageSlots());
        statement.setInt(12, data.getRadius());
        statement.setInt(13, data.getSpeedTicks());
        statement.setString(14, storageJson);
        statement.setBoolean(15, data.isAutoRepair());
        statement.setBoolean(16, data.isAutoSell());
        statement.setBoolean(17, data.isAutoSmelt());
        statement.setString(18, data.getEntityUuid() != null ? data.getEntityUuid().toString() : null);
        statement.setLong(19, data.getCreatedAt());
        statement.setLong(20, data.getUpdatedAt());
    }

    private MinionData mapRow(ResultSet resultSet) throws SQLException {
        String entityUuidRaw = resultSet.getString("entity_uuid");
        return new MinionData(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                MinionType.valueOf(resultSet.getString("type")),
                resultSet.getInt("level"),
                resultSet.getLong("xp"),
                resultSet.getInt("energy"),
                resultSet.getInt("fuel_ticks_remaining"),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getInt("storage_slots"),
                resultSet.getInt("radius"),
                resultSet.getInt("speed_ticks"),
                resultSet.getBoolean("auto_repair"),
                resultSet.getBoolean("auto_sell"),
                resultSet.getBoolean("auto_smelt"),
                resultSet.getLong("created_at"),
                resultSet.getLong("updated_at"),
                entityUuidRaw != null ? UUID.fromString(entityUuidRaw) : null
        );
    }
    }
