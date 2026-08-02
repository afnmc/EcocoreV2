package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates tables backing the local AI economy engine's learning data:
 * raw feature samples used for trend analysis, and the current
 * per-item learned weight profile.
 */
public final class V6__AiLearning implements Migration {

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    public String getDescription() {
        return "Create ai_learning_samples and ai_weight_profile tables";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_learning_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_id TEXT NOT NULL,
                        features_json TEXT NOT NULL,
                        resulting_price REAL NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ai_weight_profile (
                        item_id TEXT PRIMARY KEY,
                        weights_json TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("CREATE INDEX IF NOT EXISTS idx_ai_samples_item ON ai_learning_samples(item_id, created_at)");
        }
    }
}