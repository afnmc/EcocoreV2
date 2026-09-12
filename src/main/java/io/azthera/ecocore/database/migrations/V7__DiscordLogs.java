package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code discord_logs} table, an audit trail of every
 * message EcoCore has sent to Discord (market updates, trade logs,
 * admin logs, crash logs).
 */
public final class V7__DiscordLogs implements Migration {

    @Override
    public int getVersion() {
        return 7;
    }

    @Override
    public String getDescription() {
        return "Create discord_logs table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        channel TEXT NOT NULL,
                        log_type TEXT NOT NULL,
                        message TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_discord_logs_channel ON discord_logs(channel, created_at)");
        }
    }
}