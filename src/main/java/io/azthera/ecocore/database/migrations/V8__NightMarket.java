package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code night_market_offers} table used to persist the
 * current night market rotation across restarts.
 */
public final class V8__NightMarket implements Migration {

    @Override
    public int getVersion() {
        return 8;
    }

    @Override
    public String getDescription() {
        return "Create night_market_offers table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS night_market_offers (
                        id TEXT PRIMARY KEY,
                        material TEXT NOT NULL,
                        price REAL NOT NULL,
                        stock INTEGER NOT NULL,
                        max_stock INTEGER NOT NULL,
                        rotation_started_at INTEGER NOT NULL
                    )
                    """);
        }
    }
}