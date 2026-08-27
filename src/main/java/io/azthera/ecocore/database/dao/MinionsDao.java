package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import org.bukkit.block.BlockFace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MinionsDao {

    private final DatabaseManager databaseManager;

    public MinionsDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long insert(MinionData data, String storagePagesJson) throws SQLException {
        String sql = """
            INSERT INTO minions_data
            (owner_uuid, type, level, xp, energy, fuel_ticks_remaining, world, x, y, z,
             radius, speed_ticks, auto_repair, facing, use_arena_mode,
             storage_page_count, storage_pages_json, entity_uuid, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindInsertOrUpdate(statement, data, storagePagesJson);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public void update(MinionData data, String storagePagesJson) throws SQLException {
        String sql = """
            UPDATE minions_data SET
                level = ?, xp = ?, energy = ?, fuel_ticks_remaining = ?, world = ?, x = ?, y = ?, z = ?,
                radius = ?, speed_ticks = ?, auto_repair = ?, facing = ?, use_arena_mode = ?,
                storage_page_count = ?, storage_pages_json = ?, entity_uuid = ?, updated_at = ?
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
            statement.setInt(9, data.getRadius());
            statement.setInt(10, data.getSpeedTicks());
            statement.setBoolean(11, data.isAutoRepair());
            statement.setString(12, data.getFacing().name());
            statement.setBoolean(13, data.isUseArenaMode());
            statement.setInt(14, data.getStoragePageCount());
            statement.setString(15, storagePagesJson);
            statement.setString(16, data.getEntityUuid() != null ? data.getEntityUuid().toString() : null);
            statement.setLong(17, data.getUpdatedAt());
            statement.setLong(18, data.getId());
            statement.executeUpdate();
        }
    }

    public void updateEntityUuid(long id, UUID entityUuid) throws SQLException {
        String sql = "UPDATE minions_data SET entity_uuid = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityUuid != null ? entityUuid.toString() : null);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM minions_data WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

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

    public String findStoragePagesJson(long id) throws SQLException {
        String sql = "SELECT storage_pages_json FROM minions_data WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("storage_pages_json") : null;
            }
        }
    }

    public String findLegacyStorageJson(long id) throws SQLException {
        String sql = "SELECT storage_json FROM minions_data WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("storage_json") : null;
            }
        } catch (SQLException legacyColumnMissing) {
            return null;
        }
    }

    private void bindInsertOrUpdate(PreparedStatement statement, MinionData data, String storagePagesJson)
            throws SQLException {
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
        statement.setInt(11, data.getRadius());
        statement.setInt(12, data.getSpeedTicks());
        statement.setBoolean(13, data.isAutoRepair());
        statement.setString(14, data.getFacing().name());
        statement.setBoolean(15, data.isUseArenaMode());
        statement.setInt(16, data.getStoragePageCount());
        statement.setString(17, storagePagesJson);
        statement.setString(18, data.getEntityUuid() != null ? data.getEntityUuid().toString() : null);
        statement.setLong(19, data.getCreatedAt());
        statement.setLong(20, data.getUpdatedAt());
    }

    private MinionData mapRow(ResultSet resultSet) throws SQLException {
        String entityUuidRaw = resultSet.getString("entity_uuid");
        String facingRaw = resultSet.getString("facing");
        BlockFace facing;
        try {
            facing = facingRaw != null ? BlockFace.valueOf(facingRaw) : BlockFace.SOUTH;
        } catch (IllegalArgumentException invalidFacing) {
            facing = BlockFace.SOUTH;
        }
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
                resultSet.getInt("radius"),
                resultSet.getInt("speed_ticks"),
                resultSet.getBoolean("auto_repair"),
                facing,
                resultSet.getBoolean("use_arena_mode"),
                Math.max(1, resultSet.getInt("storage_page_count")),
                resultSet.getLong("created_at"),
                resultSet.getLong("updated_at"),
                entityUuidRaw != null ? UUID.fromString(entityUuidRaw) : null
        );
    }
}
