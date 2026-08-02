package io.azthera.ecocore.config;

import io.azthera.ecocore.model.MinionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed view of {@code minions.yml}: global minion progression/upgrade
 * rules, per-type efficiency definitions, and AI pathfinding tuning.
 */
public final class MinionsConfig {

    /**
     * Static definition of a single minion type.
     *
     * @param displayName    colorized display name
     * @param baseEfficiency baseline efficiency multiplier for this minion type
     */
    public record MinionDefinition(String displayName, double baseEfficiency) {
    }

    private final int maxLevel;
    private final int baseStorageSlots;
    private final int storageSlotsPerUpgrade;
    private final int maxStorageUpgrades;
    private final int baseRadius;
    private final int radiusPerUpgrade;
    private final int maxRadiusUpgrades;
    private final int baseSpeedTicks;
    private final int speedReductionPerUpgradeTicks;
    private final int minSpeedTicks;
    private final int baseEnergy;
    private final int energyDrainPerAction;
    private final List<String> fuelTypes;
    private final boolean autoRepairEnabled;
    private final boolean autoSellEnabled;
    private final boolean autoSmeltEnabled;

    private final Map<MinionType, MinionDefinition> minionDefinitions = new EnumMap<>(MinionType.class);

    private final boolean obstacleAvoidanceEnabled;
    private final String targetSelectionStrategy;
    private final int pathfindingMaxNodes;
    private final int pathfindingRecalculateTicks;

    /**
     * Parses minions configuration from the loaded {@code minions.yml}.
     *
     * @param config the loaded minions.yml
     */
    public MinionsConfig(FileConfiguration config) {
        this.maxLevel = config.getInt("global.max-level", 50);
        this.baseStorageSlots = config.getInt("global.base-storage-slots", 9);
        this.storageSlotsPerUpgrade = config.getInt("global.storage-slots-per-upgrade", 9);
        this.maxStorageUpgrades = config.getInt("global.max-storage-upgrades", 6);
        this.baseRadius = config.getInt("global.base-radius", 6);
        this.radiusPerUpgrade = config.getInt("global.radius-per-upgrade", 2);
        this.maxRadiusUpgrades = config.getInt("global.max-radius-upgrades", 5);
        this.baseSpeedTicks = config.getInt("global.base-speed-ticks", 20);
        this.speedReductionPerUpgradeTicks = config.getInt("global.speed-reduction-per-upgrade-ticks", 2);
        this.minSpeedTicks = config.getInt("global.min-speed-ticks", 4);
        this.baseEnergy = config.getInt("global.base-energy", 1000);
        this.energyDrainPerAction = config.getInt("global.energy-drain-per-action", 1);
        this.fuelTypes = config.getStringList("global.fuel-types");
        this.autoRepairEnabled = config.getBoolean("global.auto-repair-enabled", true);
        this.autoSellEnabled = config.getBoolean("global.auto-sell-enabled", true);
        this.autoSmeltEnabled = config.getBoolean("global.auto-smelt-enabled", true);

        ConfigurationSection minionsSection = config.getConfigurationSection("minions");
        if (minionsSection != null) {
            for (String key : minionsSection.getKeys(false)) {
                MinionType type = MinionType.fromConfigKey(key);
                if (type == null) {
                    continue;
                }
                ConfigurationSection section = minionsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                minionDefinitions.put(type, new MinionDefinition(
                        section.getString("display-name", type.name()),
                        section.getDouble("base-efficiency", 1.0)
                ));
            }
        }

        this.obstacleAvoidanceEnabled = config.getBoolean("ai.obstacle-avoidance-enabled", true);
        this.targetSelectionStrategy = config.getString("ai.target-selection-strategy", "NEAREST_HIGHEST_VALUE");
        this.pathfindingMaxNodes = config.getInt("ai.pathfinding-max-nodes", 200);
        this.pathfindingRecalculateTicks = config.getInt("ai.pathfinding-recalculate-ticks", 40);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getBaseStorageSlots() {
        return baseStorageSlots;
    }

    public int getStorageSlotsPerUpgrade() {
        return storageSlotsPerUpgrade;
    }

    public int getMaxStorageUpgrades() {
        return maxStorageUpgrades;
    }

    public int getBaseRadius() {
        return baseRadius;
    }

    public int getRadiusPerUpgrade() {
        return radiusPerUpgrade;
    }

    public int getMaxRadiusUpgrades() {
        return maxRadiusUpgrades;
    }

    public int getBaseSpeedTicks() {
        return baseSpeedTicks;
    }

    public int getSpeedReductionPerUpgradeTicks() {
        return speedReductionPerUpgradeTicks;
    }

    public int getMinSpeedTicks() {
        return minSpeedTicks;
    }

    public int getBaseEnergy() {
        return baseEnergy;
    }

    public int getEnergyDrainPerAction() {
        return energyDrainPerAction;
    }

    public List<String> getFuelTypes() {
        return fuelTypes;
    }

    public boolean isAutoRepairEnabled() {
        return autoRepairEnabled;
    }

    public boolean isAutoSellEnabled() {
        return autoSellEnabled;
    }

    public boolean isAutoSmeltEnabled() {
        return autoSmeltEnabled;
    }

    /**
     * Returns the static display/efficiency definition for a minion type.
     *
     * @param type the minion type
     * @return the definition, or {@code null} if not configured
     */
    public MinionDefinition getDefinition(MinionType type) {
        return minionDefinitions.get(type);
    }

    public boolean isObstacleAvoidanceEnabled() {
        return obstacleAvoidanceEnabled;
    }

    public String getTargetSelectionStrategy() {
        return targetSelectionStrategy;
    }

    public int getPathfindingMaxNodes() {
        return pathfindingMaxNodes;
    }

    public int getPathfindingRecalculateTicks() {
        return pathfindingRecalculateTicks;
    }
}