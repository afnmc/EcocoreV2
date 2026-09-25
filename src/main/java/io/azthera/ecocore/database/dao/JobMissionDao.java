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
     * Atomically advances a mission's progress and optionally marks it completed.
     * Guaranteed to return true only if this invocation transitioned completed from 0 to 1.
     *
     * @param id the mission id
     * @param progress the new progress value
     * @param markCompleted whether this progress reaches or exceeds target
     * @return true if this call transitioned the mission to completed = 1; false otherwise
     * @throws SQLException if the update fails
     */
    /**
     * Atomically completes a mission and credits its reward. The mission state,
     * player balance, and money ledger entry are committed on the same SQLite
     * transaction. If the mission was already completed, nothing is changed.
     */
    public AtomicRewardResult completeAndRewardAtomic(UUID playerUuid, long missionId, int progress,
                                                       double reward, String reason) throws SQLException {
        if (reward < 0) {
            throw new IllegalArgumentException("Mission reward cannot be negative");
        }

        String missionSql = "UPDATE job_missions SET progress = ?, completed = 1 WHERE id = ? AND player_uuid = ? AND completed = 0";
        String accountSql = "UPDATE player_accounts SET balance = balance + ?, updated_at = ? WHERE uuid = ?";
        String balanceSql = "SELECT balance FROM player_accounts WHERE uuid = ?";
        String ledgerSql = "INSERT INTO money_ledger (player_uuid, change_amount, balance_after, reason, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int missionUpdated;
                try (PreparedStatement statement = connection.prepareStatement(missionSql)) {
                    statement.setInt(1, progress);
                    statement.setLong(2, missionId);
                    statement.setString(3, playerUuid.toString());
                    missionUpdated = statement.executeUpdate();
                }
                if (missionUpdated == 0) {
                    connection.commit();
                    return new AtomicRewardResult(false, 0.0);
                }

                long now = System.currentTimeMillis();
                int accountUpdated;
                try (PreparedStatement statement = connection.prepareStatement(accountSql)) {
                    statement.setDouble(1, reward);
                    statement.setLong(2, now);
                    statement.setString(3, playerUuid.toString());
                    accountUpdated = statement.executeUpdate();
                }
                if (accountUpdated == 0) {
                    throw new SQLException("Player account not found for mission reward: " + playerUuid);
                }

                double newBalance;
                try (PreparedStatement statement = connection.prepareStatement(balanceSql)) {
                    statement.setString(1, playerUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new SQLException("Player account disappeared during mission reward: " + playerUuid);
                        }
                        newBalance = resultSet.getDouble(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(ledgerSql)) {
                    statement.setString(1, playerUuid.toString());
                    statement.setDouble(2, reward);
                    statement.setDouble(3, newBalance);
                    statement.setString(4, reason);
                    statement.setLong(5, now);
                    statement.executeUpdate();
                }

                connection.commit();
                return new AtomicRewardResult(true, newBalance);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public record AtomicRewardResult(boolean completed, double newBalance) {
    }

    public boolean advanceProgress(long id, int progress, boolean markCompleted) throws SQLException {
        if (markCompleted) {
            String sql = "UPDATE job_missions SET progress = ?, completed = 1 WHERE id = ? AND completed = 0";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, progress);
                statement.setLong(2, id);
                return statement.executeUpdate() > 0;
            }
        } else {
            String sql = "UPDATE job_missions SET progress = ? WHERE id = ? AND completed = 0";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, progress);
                statement.setLong(2, id);
                statement.executeUpdate();
                return false;
            }
        }
    }

    /**
     * Rolls back a mission completion if rewarding fails.
     *
     * @param id the mission id
     * @param rollbackProgress the progress value to reset to
     * @throws SQLException if the update fails
     */
    public void rollbackCompletion(long id, int rollbackProgress) throws SQLException {
        String sql = "UPDATE job_missions SET progress = ?, completed = 0 WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rollbackProgress);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updateProgress(long id, int progress, boolean completed) throws SQLException {
        advanceProgress(id, progress, completed);
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

    /**
     * Deletes every mission (active or already completed) a player has
     * for a single job (Revisi 20, used when leaving a job via
     * {@code /jobs} so old missions for that job don't linger).
     *
     * @param playerUuid the player's uuid
     * @param jobType    the job type to clear missions for
     * @throws SQLException if the delete fails
     */
    public void deleteAllForPlayerAndJob(UUID playerUuid, JobType jobType) throws SQLException {
        String sql = "DELETE FROM job_missions WHERE player_uuid = ? AND job_type = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, jobType.name());
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