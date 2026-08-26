package io.azthera.ecocore.config;

import io.azthera.ecocore.model.JobType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed view of {@code jobs.yml}: global job progression rules
 * and per-job display/reward definitions.
 */
public final class JobsConfig {

    /**
     * Static definition of a single job type's display and base rewards.
     *
     * @param displayName        colorized display name
     * @param icon                Bukkit Material name used as the GUI icon
     * @param baseMoneyPerAction  base money awarded per completed action
     * @param baseXpPerAction     base xp awarded per completed action
     */
    public record JobDefinition(String displayName, String icon, double baseMoneyPerAction, double baseXpPerAction) {
    }

    private final int maxLevel;
    private final int xpCurveBase;
    private final double xpCurveMultiplier;
    private final boolean prestigeEnabled;
    private final int prestigeMax;
    private final double prestigeXpMultiplierBonus;
    private final int dailyMissionCount;
    private final int weeklyMissionCount;
    private final int leaderboardTopSize;
    private final int leaderboardRefreshSeconds;

    private final Map<JobType, JobDefinition> jobDefinitions = new EnumMap<>(JobType.class);

    private final List<String> perkBonusTypes;
    private final List<Integer> perkUnlockLevels;
    private final int skillTreePointsPerLevel;
    private final int skillTreeMaxBranches;

    /**
     * Parses jobs configuration from the loaded {@code jobs.yml}.
     *
     * @param config the loaded jobs.yml
     */
    public JobsConfig(FileConfiguration config) {
        this.maxLevel = config.getInt("global.max-level", 100);
        this.xpCurveBase = config.getInt("global.xp-curve-base", 100);
        this.xpCurveMultiplier = config.getDouble("global.xp-curve-multiplier", 1.08);
        this.prestigeEnabled = config.getBoolean("global.prestige-enabled", true);
        this.prestigeMax = config.getInt("global.prestige-max", 10);
        this.prestigeXpMultiplierBonus = config.getDouble("global.prestige-xp-multiplier-bonus", 0.05);
        this.dailyMissionCount = config.getInt("global.daily-mission-count", 3);
        this.weeklyMissionCount = config.getInt("global.weekly-mission-count", 5);
        this.leaderboardTopSize = config.getInt("global.leaderboard-top-size", 10);
        this.leaderboardRefreshSeconds = config.getInt("global.leaderboard-refresh-seconds", 300);

        ConfigurationSection jobsSection = config.getConfigurationSection("jobs");
        if (jobsSection != null) {
            for (String key : jobsSection.getKeys(false)) {
                JobType type = JobType.fromConfigKey(key);
                if (type == null) {
                    continue;
                }
                ConfigurationSection section = jobsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                jobDefinitions.put(type, new JobDefinition(
                        section.getString("display-name", type.name()),
                        section.getString("icon", "STONE"),
                        section.getDouble("base-money-per-action", 1.0),
                        section.getDouble("base-xp-per-action", 2.0)
                ));
            }
        }

        this.perkBonusTypes = config.getStringList("perks.bonus-types");
        this.perkUnlockLevels = config.getIntegerList("perks.perk-unlock-levels");
        this.skillTreePointsPerLevel = config.getInt("skill-tree.points-per-level", 1);
        this.skillTreeMaxBranches = config.getInt("skill-tree.max-branches", 3);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getXpCurveBase() {
        return xpCurveBase;
    }

    public double getXpCurveMultiplier() {
        return xpCurveMultiplier;
    }

    /**
     * Computes the total xp required to reach a given level, using
     * the configured exponential curve: base * multiplier^(level-1).
     *
     * @param level the target level (1-based)
     * @return the cumulative xp required to reach that level
     */
    public long xpRequiredForLevel(int level) {
        if (level <= 1) {
            return 0L;
        }
        return Math.round(xpCurveBase * Math.pow(xpCurveMultiplier, level - 1));
    }

    public boolean isPrestigeEnabled() {
        return prestigeEnabled;
    }

    public int getPrestigeMax() {
        return prestigeMax;
    }

    public double getPrestigeXpMultiplierBonus() {
        return prestigeXpMultiplierBonus;
    }

    public int getDailyMissionCount() {
        return dailyMissionCount;
    }

    public int getWeeklyMissionCount() {
        return weeklyMissionCount;
    }

    public int getLeaderboardTopSize() {
        return leaderboardTopSize;
    }

    public int getLeaderboardRefreshSeconds() {
        return leaderboardRefreshSeconds;
    }

    /**
     * Returns the static display/reward definition for a job type.
     *
     * @param type the job type
     * @return the definition, or {@code null} if not configured
     */
    public JobDefinition getDefinition(JobType type) {
        return jobDefinitions.get(type);
    }

    public List<String> getPerkBonusTypes() {
        return perkBonusTypes;
    }

    public List<Integer> getPerkUnlockLevels() {
        return perkUnlockLevels;
    }

    public int getSkillTreePointsPerLevel() {
        return skillTreePointsPerLevel;
    }

    public int getSkillTreeMaxBranches() {
        return skillTreeMaxBranches;
    }
}