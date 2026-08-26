// FILE: src/main/java/io/azthera/ecocore/database/migrations/V14__MinionConnections.java
package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the new {@code minion_link_connections} table for Revisi
 * 9's rebuilt connector system, replacing {@code minion_connections}
 * (kept intact for backward compatibility / historical data, but no
 * longer written to). Unlike the old table, each row also records
 * whether the link is DIRECT (max 10 blocks, free) or RELAY (routed
 * through a {@code minion_connector_entities} row, upgradeable
 * range), and the relay connector id when applicable.
 */
public final class V14__MinionConnections implements Migration {

    @Override
    public int getVersion() {
        return 14;
    }

    @Override
    public String getDescription() {
        return "Create minion_link_connections table for the new direct/relay connector system";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS minion_link_connections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    source_minion_id INTEGER NOT NULL,
                    destination_minion_id INTEGER NOT NULL,
                    link_mode TEXT NOT NULL DEFAULT 'DIRECT',
                    relay_connector_id INTEGER,
                    created_at INTEGER NOT NULL,
                    UNIQUE(source_minion_id, destination_minion_id)
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_minion_link_connections_source "
                    + "ON minion_link_connections(source_minion_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_minion_link_connections_owner "
                    + "ON minion_link_connections(owner_uuid)");
        }
    }
}