package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code jobs_data} table.
 */
public final class JobsDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a jobs DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public JobsDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Finds a player's progress in a single job.
     *
     * @param playerUuid the player's uuid
     * @param jobType    the job type
     * @return the job data, or {@code null} if the player has not joined this job
     * @throws SQLException if the query fails
     */
    public JobData find(UUID playerUuid, JobType jobType) throws SQLException {
        String sql = "SELECT * FROM jobs_data WHERE player_uuid = ? AND job_type = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, jobType.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        }
    }

    /**
     * Finds every job a player has joined.
     *
     * @param playerUuid the player's uuid
     * @return the list of job progress records for that player
     * @throws SQLException if the query fails
     */
    public List<JobData> findAllForPlayer(UUID playerUuid) throws SQLException {
        String sql = "SELECT * FROM jobs_data WHERE player_uuid = ?";
        List<JobData> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    /**
     * Inserts or fully overwrites a player's progress in a job.
     *
     * @param data the job data to persist
     * @throws SQLException if the operation fails
     */
    public void upsert(JobData data) throws SQLException {
        String sql = """
                INSERT INTO jobs_data (player_uuid, job_type, xp, level, prestige, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, job_type) DO UPDATE SET
                    xp = excluded.xp,
                    level = excluded.level,
                    prestige = excluded.prestige,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, data.getPlayerUuid().toString());
            statement.setString(2, data.getJobType().name());
            statement.setLong(3, data.getXp());
            statement.setInt(4, data.getLevel());
            statement.setInt(5, data.getPrestige());
            statement.setLong(6, data.getUpdatedAt());
            statement.executeUpdate();
        }
    }

    /**
     * Finds the top players for a given job ranked by level then xp,
     * used to back {@code JobLeaderboardGui} and {@code /jobs} leaderboards.
     *
     * @param jobType the job type to rank
     * @param limit   maximum number of entries to return
     * @return the leaderboard entries, highest ranked first
     * @throws SQLException if the query fails
     */
    public List<JobData> topByJob(JobType jobType, int limit) throws SQLException {
        String sql = """
                SELECT * FROM jobs_data
                WHERE job_type = ?
                ORDER BY prestige DESC, level DESC, xp DESC
                LIMIT ?
                """;
        List<JobData> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobType.name());
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    private JobData mapRow(ResultSet resultSet) throws SQLException {
        return new JobData(
                UUID.fromString(resultSet.getString("player_uuid")),
                JobType.valueOf(resultSet.getString("job_type")),
                resultSet.getLong("xp"),
                resultSet.getInt("level"),
                resultSet.getInt("prestige"),
                resultSet.getLong("updated_at")
        );
    }
}