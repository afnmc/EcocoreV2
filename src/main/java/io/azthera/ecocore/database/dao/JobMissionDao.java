package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.JobMissionRecord;
import io.azthera.ecocore.model.JobType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access object for the {@code job_missions} table.
 */
public final class JobMissionDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a job mission DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public JobMissionDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Assigns a new mission to a player.
     *
     * @param playerUuid the player receiving the mission
     * @param jobType    the job this mission belongs to
     * @param missionKey a short key identifying the mission template
     * @param period     "DAILY" or "WEEKLY"
     * @param target     the progress value required to complete the mission
     * @param assignedAt epoch millis of assignment
     * @return the generated row id
     * @throws SQLException if the insert fails
     */
    public long insert(UUID playerUuid, JobType jobType, String missionKey, String period,
                        int target, long assignedAt) throws SQLException {
        String sql = """
                INSERT INTO job_missions (player_uuid, job_type, mission_key, period, progress, target, completed, assigned_at)
                VALUES (?, ?, ?, ?, 0, ?, 0, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, jobType.name());
            statement.setString(3, missionKey);
            statement.setString(4, period);
            statement.setInt(5, target);
            statement.setLong(6, assignedAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /**
     * Finds every incomplete mission currently assigned to a player, across all jobs.
     *
     * @param playerUuid the player's uuid
     * @return the player's active missions
     * @throws SQLException if the query fails
     */
    public List<JobMissionRecord> findActiveForPlayer(UUID playerUuid) throws SQLException {
        String sql = "SELECT * FROM job_missions WHERE player_uuid = ? AND completed = 0";
        List<JobMissionRecord> results = new ArrayList<>();
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
     * Finds every incomplete mission for a player within a single job.
     *
     * @param playerUuid the player's uuid
     * @param jobType    the job type to filter by
     * @return the matching active missions
     * @throws SQLException if the query fails
     */
    public List<JobMissionRecord> findActiveForPlayerAndJob(UUID playerUuid, JobType jobType) throws SQLException {
        String sql = "SELECT * FROM job_missions WHERE player_uuid = ? AND job_type = ? AND completed = 0";
        List<JobMissionRecord> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, jobType.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    /**
     * Updates a mission's progress and completion state.
     *
     * @param id        the mission's database id
     * @param progress  the new progress value
     * @param completed whether the mission is now complete
     * @throws SQLException if the update fails
     */
    public void updateProgress(long id, int progress, boolean completed) throws SQLException {
        String sql = "UPDATE job_missions SET progress = ?, completed = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, progress);
            statement.setBoolean(2, completed);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    /**
     * Deletes every mission of a given period assigned before a cutoff,
     * used to clear out stale daily/weekly missions before reassigning new ones.
     *
     * @param period       "DAILY" or "WEEKLY"
     * @param beforeMillis exclusive upper bound epoch millis
     * @throws SQLException if the delete fails
     */
    public void deleteForPeriodBefore(String period, long beforeMillis) throws SQLException {
        String sql = "DELETE FROM job_missions WHERE period = ? AND assigned_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, period);
            statement.setLong(2, beforeMillis);
            statement.executeUpdate();
        }
    }

    private JobMissionRecord mapRow(ResultSet resultSet) throws SQLException {
        return new JobMissionRecord(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                JobType.valueOf(resultSet.getString("job_type")),
                resultSet.getString("mission_key"),
                resultSet.getString("period"),
                resultSet.getInt("progress"),
                resultSet.getInt("target"),
                resultSet.getBoolean("completed"),
                resultSet.getLong("assigned_at")
        );
    }
}