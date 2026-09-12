package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code inflation_history} table used to store periodic
 * macro-economic snapshots computed by the InflationEngine.
 */
public final class V3__InflationHistory implements Migration {

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public String getDescription() {
        return "Create inflation_history table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inflation_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        total_money REAL NOT NULL,
                        average_balance REAL NOT NULL,
                        trading_volume INTEGER NOT NULL,
                        money_flow REAL NOT NULL,
                        market_activity REAL NOT NULL,
                        inflation_percent REAL NOT NULL,
                        deflation_percent REAL NOT NULL,
                        recovery_percent REAL NOT NULL,
                        state TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inflation_history_time ON inflation_history(created_at)");
        }
    }
}