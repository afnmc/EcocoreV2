package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code minions_data} table storing every placed minion's
 * persistent state, including location, upgrades, and inventory contents.
 */
public final class V5__MinionsData implements Migration {

    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public String getDescription() {
        return "Create minions_data table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS minions_data (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid TEXT NOT NULL,
                        type TEXT NOT NULL,
                        level INTEGER NOT NULL DEFAULT 1,
                        xp INTEGER NOT NULL DEFAULT 0,
                        energy INTEGER NOT NULL DEFAULT 0,
                        fuel_ticks_remaining INTEGER NOT NULL DEFAULT 0,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        storage_slots INTEGER NOT NULL DEFAULT 9,
                        radius INTEGER NOT NULL DEFAULT 6,
                        speed_ticks INTEGER NOT NULL DEFAULT 20,
                        storage_json TEXT,
                        auto_repair INTEGER NOT NULL DEFAULT 0,
                        auto_sell INTEGER NOT NULL DEFAULT 0,
                        auto_smelt INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_minions_owner ON minions_data(owner_uuid)");
        }
    }
}