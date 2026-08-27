package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.database.dao.JobMissionDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.JobMissionRecord;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.model.MissionType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Assigns and tracks daily/weekly job missions, and notifies the
 * player in chat (with a reward summary and sound) the moment a
 * mission is completed.
 *
 * <p>Revisi 18: missions are drawn from each job's configured
 * {@code jobs.yml mission-pools} rather than always being the single
 * generic "do X actions" template. Also adds basic anti-abuse: a
 * minimum cooldown between counted actions toward the same mission,
 * and a hard daily action cap per mission.
 */
public final class JobMissionManager {

    public static final String PERIOD_DAILY = "DAILY";
    public static final String PERIOD_WEEKLY = "WEEKLY";

    private static final String FALLBACK_TEMPLATE_KEY = "GENERIC_ACTION:";
    private static final int FALLBACK_TARGET_MIN = 20;
    private static final int FALLBACK_TARGET_MAX = 60;
    private static final double FALLBACK_MONEY_PER_TARGET_UNIT = 2.0;

    private final JobMissionDao jobMissionDao;
    private final JobsConfig jobsConfig;
    private final EconomyEngine economyEngine;
    private final MessagesConfig messagesConfig;

    private final Map<Long, Long> lastActionAtByMissionId = new ConcurrentHashMap<>();
    private final Map<Long, DailyCounter> dailyActionCounters = new ConcurrentHashMap<>();

    private record DailyCounter(long dayEpoch, int count) {
    }

    public JobMissionManager(JobMissionDao jobMissionDao, JobsConfig jobsConfig,
                              EconomyEngine economyEngine, MessagesConfig messagesConfig) {
        this.jobMissionDao = jobMissionDao;
        this.jobsConfig = jobsConfig;
        this.economyEngine = economyEngine;
        this.messagesConfig = messagesConfig;
    }

    public void assignDailyMissions(UUID playerUuid, JobType jobType) throws SQLException {
        assignMissions(playerUuid, jobType, PERIOD_DAILY, jobsConfig.getDailyMissionCount());
    }

    public void assignWeeklyMissions(UUID playerUuid, JobType jobType) throws SQLException {
        assignMissions(playerUuid, jobType, PERIOD_WEEKLY, jobsConfig.getWeeklyMissionCount());
    }

    private void assignMissions(UUID playerUuid, JobType jobType, String period, int count) throws SQLException {
        long now = System.currentTimeMillis();
        List<MissionTemplate> pool = jobsConfig.getMissionPool(jobType);
        for (int i = 0; i < count; i++) {
            MissionTemplate template = pickWeightedTemplate(pool, period);
            String missionKey;
            int target;
            if (template != null) {
                missionKey = template.toStorageKey();
                target = ThreadLocalRandom.current().nextInt(template.minAmount(), template.maxAmount() + 1);
            } else {
                missionKey = FALLBACK_TEMPLATE_KEY;
                target = ThreadLocalRandom.current().nextInt(FALLBACK_TARGET_MIN, FALLBACK_TARGET_MAX + 1);
            }
            jobMissionDao.insert(playerUuid, jobType, missionKey, period, target, now);
        }
    }

    private MissionTemplate pickWeightedTemplate(List<MissionTemplate> pool, String period) {
        List<MissionTemplate> eligible = pool.stream()
                .filter(template -> period.equals(PERIOD_DAILY) ? template.dailyEligible() : template.weeklyEligible())
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }
        double totalWeight = eligible.stream().mapToDouble(MissionTemplate::weight).sum();
        if (totalWeight <= 0) {
            return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        }
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (MissionTemplate template : eligible) {
            cumulative += template.weight();
            if (roll <= cumulative) {
                return template;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    public List<JobMissionRecord> getActiveMissions(UUID playerUuid) throws SQLException {
        return jobMissionDao.findActiveForPlayer(playerUuid);
    }

    /**
     * Records that a player performed one job action of a specific
     * {@link MissionType} (optionally against a specific target),
     * advancing every active mission that matches both.
     *
     * @param playerUuid the acting player's uuid
     * @param jobType the job the action belongs to
     * @param missionType the specific action type that occurred
     * @param target the specific material/entity/item involved, or {@code null} if not target-specific
     * @param weight how much this single action counts toward mission progress
     * @param moneyScale a scale factor applied to mission completion rewards
     * @throws SQLException if the underlying persistence fails
     */
    public void recordActionForMissions(UUID playerUuid, JobType jobType, MissionType missionType,
                                         String target, int weight, double moneyScale) throws SQLException {
        List<JobMissionRecord> active = jobMissionDao.findActiveForPlayerAndJob(playerUuid, jobType);
        for (JobMissionRecord mission : active) {
            if (!missionMatches(mission, missionType, target)) {
                continue;
            }
            if (!passesAntiAbuseChecks(mission)) {
                continue;
            }
            int newProgress = Math.min(mission.target(), mission.progress() + weight);
            boolean nowComplete = newProgress >= mission.target();
            jobMissionDao.updateProgress(mission.id(), newProgress, nowComplete);
            if (nowComplete && !mission.completed()) {
                double moneyPerUnit = resolveMoneyPerUnit(mission);
                double reward = mission.target() * moneyPerUnit * moneyScale;
                economyEngine.deposit(playerUuid, reward, TransactionLogger.REASON_MISSION_REWARD);
                notifyMissionComplete(playerUuid, mission, reward);
            }
        }
    }

    /**
     * Legacy overload for callers not yet updated to pass a specific
     * {@link MissionType} - treats every active mission as matching.
     *
     * @deprecated use {@link #recordActionForMissions(UUID, JobType, MissionType, String, int, double)}
     */
    @Deprecated
    public void recordActionForMissions(UUID playerUuid, JobType jobType, int weight, double moneyScale)
            throws SQLException {
        List<JobMissionRecord> active = jobMissionDao.findActiveForPlayerAndJob(playerUuid, jobType);
        for (JobMissionRecord mission : active) {
            if (!passesAntiAbuseChecks(mission)) {
                continue;
            }
            int newProgress = Math.min(mission.target(), mission.progress() + weight);
            boolean nowComplete = newProgress >= mission.target();
            jobMissionDao.updateProgress(mission.id(), newProgress, nowComplete);
            if (nowComplete && !mission.completed()) {
                double reward = mission.target() * FALLBACK_MONEY_PER_TARGET_UNIT * moneyScale;
                economyEngine.deposit(playerUuid, reward, TransactionLogger.REASON_MISSION_REWARD);
                notifyMissionComplete(playerUuid, mission, reward);
            }
        }
    }

    private boolean missionMatches(JobMissionRecord mission, MissionType missionType, String target) {
        MissionTemplate.DecodedKey decoded = MissionTemplate.decode(mission.missionKey());
        if (decoded == null) {
            return true;
        }
        if (decoded.type() != missionType) {
            return false;
        }
        return decoded.target() == null || decoded.target().equalsIgnoreCase(target);
    }

    private double resolveMoneyPerUnit(JobMissionRecord mission) {
        MissionTemplate.DecodedKey decoded = MissionTemplate.decode(mission.missionKey());
        if (decoded == null) {
            return FALLBACK_MONEY_PER_TARGET_UNIT;
        }
        return jobsConfig.getMissionPool(mission.jobType()).stream()
                .filter(template -> template.type() == decoded.type()
                        && java.util.Objects.equals(template.target(), decoded.target()))
                .findFirst()
                .map(MissionTemplate::moneyPerUnit)
                .orElse(FALLBACK_MONEY_PER_TARGET_UNIT);
    }

    private boolean passesAntiAbuseChecks(JobMissionRecord mission) {
        long now = System.currentTimeMillis();
        long cooldownMillis = (long) (jobsConfig.getAntiAbuseCooldownSeconds() * 1000L);
        Long lastActionAt = lastActionAtByMissionId.get(mission.id());
        if (lastActionAt != null && (now - lastActionAt) < cooldownMillis) {
            return false;
        }
        long currentDayBucket = now / 86_400_000L;
        DailyCounter counter = dailyActionCounters.get(mission.id());
        int countToday = (counter != null && counter.dayEpoch() == currentDayBucket) ? counter.count() : 0;
        if (countToday >= jobsConfig.getAntiAbuseDailyActionCap()) {
            return false;
        }
        lastActionAtByMissionId.put(mission.id(), now);
        dailyActionCounters.put(mission.id(), new DailyCounter(currentDayBucket, countToday + 1));
        return true;
    }

    private void notifyMissionComplete(UUID playerUuid, JobMissionRecord mission, double reward) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return;
        }
        String periodLabel = mission.period().equals(PERIOD_DAILY) ? "Harian" : "Mingguan";
        player.sendMessage(messagesConfig.getWithPrefix("jobs.mission-complete",
                "period", periodLabel, "reward", String.format("%.2f", reward)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
    }

    public void pruneOldMissions(String period, long beforeMillis) throws SQLException {
        jobMissionDao.deleteForPeriodBefore(period, beforeMillis);
    }
}
