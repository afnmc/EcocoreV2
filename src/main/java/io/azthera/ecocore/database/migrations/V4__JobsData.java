package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates tables backing the Jobs system: per-player job progress
 * and daily/weekly mission tracking.
 */
public final class V4__JobsData implements Migration {

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public String getDescription() {
        return "Create jobs_data and job_missions tables";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS jobs_data (
                        player_uuid TEXT NOT NULL,
                        job_type TEXT NOT NULL,
                        xp INTEGER NOT NULL DEFAULT 0,
                        level INTEGER NOT NULL DEFAULT 1,
                        prestige INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, job_type)
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS job_missions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        job_type TEXT NOT NULL,
                        mission_key TEXT NOT NULL,
                        period TEXT NOT NULL,
                        progress INTEGER NOT NULL DEFAULT 0,
                        target INTEGER NOT NULL,
                        completed INTEGER NOT NULL DEFAULT 0,
                        assigned_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("CREATE INDEX IF NOT EXISTS idx_job_missions_player ON job_missions(player_uuid, period)");
        }
    }
}