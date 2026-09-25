package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.TransactionRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code buy_history} table.
 */
public final class BuyHistoryDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a buy history DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public BuyHistoryDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Records a completed purchase.
     *
     * @param playerUuid the buying player
     * @param itemId     the purchased item's id
     * @param amount     quantity purchased
     * @param unitPrice  price per single item
     * @param totalPrice total price paid
     * @throws SQLException if the insert fails
     */
    public void insert(UUID playerUuid, String itemId, int amount, double unitPrice, double totalPrice) throws SQLException {
        String sql = """
                INSERT INTO buy_history (player_uuid, item_id, amount, unit_price, total_price, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, itemId);
            statement.setInt(3, amount);
            statement.setDouble(4, unitPrice);
            statement.setDouble(5, totalPrice);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Finds the most recent purchases made by a player, newest first,
     * used to back the shop's purchase history GUI.
     *
     * @param playerUuid the player to look up
     * @param limit      maximum number of records to return
     * @return the matching transactions, newest first
     * @throws SQLException if the query fails
     */
    public List<TransactionRecord> findRecentByPlayer(UUID playerUuid, int limit) throws SQLException {
        String sql = """
                SELECT id, player_uuid, item_id, amount, unit_price, total_price, created_at
                FROM buy_history
                WHERE player_uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<TransactionRecord> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    /**
     * Counts total purchases of a given item since a given time, used by
     * the AI engine's demand indicator.
     *
     * @param itemId      the item id
     * @param sinceMillis inclusive lower bound epoch millis
     * @return the number of buy transactions in the window
     * @throws SQLException if the query fails
     */
    public int countSince(String itemId, long sinceMillis) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM buy_history WHERE item_id = ? AND created_at >= ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setLong(2, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("cnt") : 0;
            }
        }
    }

    /**
     * Counts every buy transaction across all items since a given time,
     * used by the InflationEngine's trading-volume indicator.
     *
     * @param sinceMillis inclusive lower bound epoch millis
     * @return the total number of buy transactions in the window
     * @throws SQLException if the query fails
     */
    public int countAllSince(long sinceMillis) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM buy_history WHERE created_at >= ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("cnt") : 0;
            }
        }
    }

    private TransactionRecord mapRow(ResultSet resultSet) throws SQLException {
        return new TransactionRecord(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getString("item_id"),
                TransactionRecord.TransactionType.BUY,
                resultSet.getInt("amount"),
                resultSet.getDouble("unit_price"),
                resultSet.getDouble("total_price"),
                resultSet.getLong("created_at")
        );
    }
}