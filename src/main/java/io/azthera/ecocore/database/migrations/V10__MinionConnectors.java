package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code minion_connections} table backing the Connector
 * Network: directed links a player draws between two of their own
 * minions with the Connector Tool, replacing the old fixed-direction
 * Collector routing. Each row is one directed edge (source pushes
 * into destination); a source may have several outgoing rows
 * (branching) and a destination may receive from several sources.
 */
public final class V10__MinionConnectors implements Migration {

    @Override
    public int getVersion() {
        return 10;
    }

    @Override
    public String getDescription() {
        return "Create minion_connections table for the Connector Network";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS minion_connections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid TEXT NOT NULL,
                        source_minion_id INTEGER NOT NULL,
                        destination_minion_id INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        UNIQUE(source_minion_id, destination_minion_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_minion_connections_source "
                    + "ON minion_connections(source_minion_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_minion_connections_owner "
                    + "ON minion_connections(owner_uuid)");
        }
    }
}
