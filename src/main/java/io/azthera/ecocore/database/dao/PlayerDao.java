package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.PlayerAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code player_accounts} table.
 */
public final class PlayerDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a player DAO backed by the given database manager.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public PlayerDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Finds a player's account by uuid.
     *
     * @param uuid the player's uuid
     * @return the account, or {@code null} if none exists yet
     * @throws SQLException if the query fails
     */
    public PlayerAccount findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, last_known_name, balance, created_at, updated_at FROM player_accounts WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapRow(resultSet);
            }
        }
    }

    /**
     * Creates a brand-new account with the given starting balance if one
     * does not already exist for this uuid. Existing accounts are left untouched.
     *
     * @param uuid           the player's uuid
     * @param name           the player's current username
     * @param startingBalance balance to assign on first creation
     * @return the existing or newly created account
     * @throws SQLException if the operation fails
     */
    public PlayerAccount findOrCreate(UUID uuid, String name, double startingBalance) throws SQLException {
        PlayerAccount existing = findByUuid(uuid);
        if (existing != null) {
            return existing;
        }

        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO player_accounts (uuid, last_known_name, balance, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setDouble(3, startingBalance);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
        return new PlayerAccount(uuid, name, startingBalance, now, now);
    }

    /**
     * Persists an account's current balance and last-known-name back to the database.
     *
     * @param account the account to save
     * @throws SQLException if the update fails
     */
    public void save(PlayerAccount account) throws SQLException {
        String sql = """
                UPDATE player_accounts
                SET last_known_name = ?, balance = ?, updated_at = ?
                WHERE uuid = ?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getLastKnownName());
            statement.setDouble(2, account.getBalance());
            statement.setLong(3, account.getUpdatedAt());
            statement.setString(4, account.getUuid().toString());
            statement.executeUpdate();
        }
    }

    /**
     * Computes the sum of all player balances, used by the InflationEngine
     * to determine total money supply.
     *
     * @return the total money currently held across all accounts
     * @throws SQLException if the query fails
     */
    public double sumTotalMoney() throws SQLException {
        String sql = "SELECT COALESCE(SUM(balance), 0) AS total FROM player_accounts";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getDouble("total") : 0.0;
        }
    }

    /**
     * Computes the average balance across all known accounts.
     *
     * @return the average balance, or 0 if no accounts exist
     * @throws SQLException if the query fails
     */
    public double averageBalance() throws SQLException {
        String sql = "SELECT COALESCE(AVG(balance), 0) AS avg_balance FROM player_accounts";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getDouble("avg_balance") : 0.0;
        }
    }

    /**
     * Counts the number of known player accounts.
     *
     * @return the account count
     * @throws SQLException if the query fails
     */
    public int countAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM player_accounts";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt("cnt") : 0;
        }
    }

    /**
     * Returns every account's balance, used by {@code WealthDistributionTracker}
     * to compute wealth concentration (Gini coefficient) across the server.
     * Callers should treat the returned list as a snapshot; it is not
     * kept in sync with concurrent balance changes.
     *
     * @return every known account's balance, in no particular order
     * @throws SQLException if the query fails
     */
    public List<Double> findAllBalances() throws SQLException {
        String sql = "SELECT balance FROM player_accounts";
        List<Double> balances = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                balances.add(resultSet.getDouble("balance"));
            }
        }
        return balances;
    }

    private PlayerAccount mapRow(ResultSet resultSet) throws SQLException {
        return new PlayerAccount(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("last_known_name"),
                resultSet.getDouble("balance"),
                resultSet.getLong("created_at"),
                resultSet.getLong("updated_at")
        );
    }
}