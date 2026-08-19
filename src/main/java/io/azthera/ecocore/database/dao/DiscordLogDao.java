package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Data access object for the {@code discord_logs} audit table.
 */
public final class DiscordLogDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a Discord log DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public DiscordLogDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Records a message that was sent (or attempted to be sent) to Discord.
     *
     * @param channel the logical channel key (e.g. "market", "trade-log", "admin-log")
     * @param logType a short type tag (e.g. "PRICE_CHANGE", "BUY", "SELL", "CRASH")
     * @param message the message content that was sent
     * @throws SQLException if the insert fails
     */
    public void insert(String channel, String logType, String message) throws SQLException {
        String sql = """
                INSERT INTO discord_logs (channel, log_type, message, created_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channel);
            statement.setString(2, logType);
            statement.setString(3, message);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Deletes log entries older than the given cutoff.
     *
     * @param beforeMillis exclusive upper bound epoch millis
     * @throws SQLException if the delete fails
     */
    public void pruneOlderThan(long beforeMillis) throws SQLException {
        String sql = "DELETE FROM discord_logs WHERE created_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, beforeMillis);
            statement.executeUpdate();
        }
    }
}