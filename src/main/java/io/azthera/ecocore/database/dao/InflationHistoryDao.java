package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.InflationRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code inflation_history} table.
 */
public final class InflationHistoryDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates an inflation history DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public InflationHistoryDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Inserts a new macro-economic snapshot.
     *
     * @param record the record to persist
     * @throws SQLException if the insert fails
     */
    public void insert(InflationRecord record) throws SQLException {
        String sql = """
                INSERT INTO inflation_history
                    (total_money, average_balance, trading_volume, money_flow, market_activity,
                     inflation_percent, deflation_percent, recovery_percent, state, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, record.totalMoney());
            statement.setDouble(2, record.averageBalance());
            statement.setLong(3, record.tradingVolume());
            statement.setDouble(4, record.moneyFlow());
            statement.setDouble(5, record.marketActivity());
            statement.setDouble(6, record.inflationPercent());
            statement.setDouble(7, record.deflationPercent());
            statement.setDouble(8, record.recoveryPercent());
            statement.setString(9, record.state().name());
            statement.setLong(10, record.timestamp());
            statement.executeUpdate();
        }
    }

    /**
     * Finds the most recently computed inflation record.
     *
     * @return the latest record, or {@code null} if none has ever been computed
     * @throws SQLException if the query fails
     */
    public InflationRecord findLatest() throws SQLException {
        String sql = """
                SELECT total_money, average_balance, trading_volume, money_flow, market_activity,
                       inflation_percent, deflation_percent, recovery_percent, state, created_at
                FROM inflation_history
                ORDER BY created_at DESC
                LIMIT 1
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? mapRow(resultSet) : null;
        }
    }

    /**
     * Finds all inflation records since a given time, oldest first,
     * used to render inflation trend charts.
     *
     * @param sinceMillis inclusive lower bound epoch millis
     * @return the matching records, oldest first
     * @throws SQLException if the query fails
     */
    public List<InflationRecord> findSince(long sinceMillis) throws SQLException {
        String sql = """
                SELECT total_money, average_balance, trading_volume, money_flow, market_activity,
                       inflation_percent, deflation_percent, recovery_percent, state, created_at
                FROM inflation_history
                WHERE created_at >= ?
                ORDER BY created_at ASC
                """;
        List<InflationRecord> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    private InflationRecord mapRow(ResultSet resultSet) throws SQLException {
        return new InflationRecord(
                resultSet.getDouble("total_money"),
                resultSet.getDouble("average_balance"),
                resultSet.getLong("trading_volume"),
                resultSet.getDouble("money_flow"),
                resultSet.getDouble("market_activity"),
                resultSet.getDouble("inflation_percent"),
                resultSet.getDouble("deflation_percent"),
                resultSet.getDouble("recovery_percent"),
                EconomicState.valueOf(resultSet.getString("state")),
                resultSet.getLong("created_at")
        );
    }
}