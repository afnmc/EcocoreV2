package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A single, ordered, idempotent schema migration.
 * Migrations are applied in ascending {@link #getVersion()} order and
 * tracked in the {@code schema_version} bookkeeping table so each
 * migration runs at most once per database.
 */
public interface Migration {

    /**
     * The migration's version number. Must be unique and strictly
     * increasing across all registered migrations.
     *
     * @return the version number
     */
    int getVersion();

    /**
     * A short human-readable description, used in log output.
     *
     * @return the migration description
     */
    String getDescription();

    /**
     * Applies this migration's schema changes using the given connection.
     * Implementations should use {@code CREATE TABLE IF NOT EXISTS} /
     * defensive checks where practical so re-runs against a partially
     * migrated database fail safely.
     *
     * @param connection an open JDBC connection
     * @throws SQLException if the schema change fails
     */
    void apply(Connection connection) throws SQLException;
}