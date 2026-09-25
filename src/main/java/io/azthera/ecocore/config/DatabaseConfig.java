package io.azthera.ecocore.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Parsed view of {@code database.yml}.
 */
public final class DatabaseConfig {

    private final String type;
    private final String fileName;
    private final boolean autoMigrate;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;
    private final long leakDetectionThresholdMs;
    private final boolean backupEnabled;
    private final int backupIntervalMinutes;
    private final int backupKeepCount;
    private final String backupFolder;

    /**
     * Parses database configuration from the loaded {@code database.yml}.
     *
     * @param config the loaded database.yml
     */
    public DatabaseConfig(FileConfiguration config) {
        this.type = config.getString("type", "SQLITE");
        this.fileName = config.getString("file-name", "ecocore.db");
        this.autoMigrate = config.getBoolean("auto-migrate", true);
        this.maxPoolSize = config.getInt("pool.maximum-pool-size", 8);
        this.minIdle = config.getInt("pool.minimum-idle", 2);
        this.connectionTimeoutMs = config.getLong("pool.connection-timeout-ms", 10000);
        this.idleTimeoutMs = config.getLong("pool.idle-timeout-ms", 600000);
        this.maxLifetimeMs = config.getLong("pool.max-lifetime-ms", 1800000);
        this.leakDetectionThresholdMs = config.getLong("pool.leak-detection-threshold-ms", 15000);
        this.backupEnabled = config.getBoolean("backup.enabled", true);
        this.backupIntervalMinutes = config.getInt("backup.interval-minutes", 60);
        this.backupKeepCount = config.getInt("backup.keep-count", 24);
        this.backupFolder = config.getString("backup.folder", "backups");
    }

    public String getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isAutoMigrate() {
        return autoMigrate;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    public long getLeakDetectionThresholdMs() {
        return leakDetectionThresholdMs;
    }

    public boolean isBackupEnabled() {
        return backupEnabled;
    }

    public int getBackupIntervalMinutes() {
        return backupIntervalMinutes;
    }

    public int getBackupKeepCount() {
        return backupKeepCount;
    }

    public String getBackupFolder() {
        return backupFolder;
    }
}