// FILE: src/main/java/io/azthera/ecocore/database/migrations/V13__MinionConnectorEntities.java
package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code minion_connector_entities} table backing
 * Revisi 9's relay connectors: standalone placeable entities (NOT
 * minions themselves) that extend the max distance a connection can
 * span, with an upgradeable range level.
 */
public final class V13__MinionConnectorEntities implements Migration {

    @Override
    public int getVersion() {
        return 13;
    }

    @Override
    public String getDescription() {
        return "Create minion_connector_entities table for relay connectors";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS minion_connector_entities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    range_level INTEGER NOT NULL DEFAULT 0,
                    entity_uuid TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_minion_connector_entities_owner "
                    + "ON minion_connector_entities(owner_uuid)");
        }
    }
}