package io.azthera.ecocore.config;

import io.azthera.ecocore.minions.types.FishRarityTier;
import io.azthera.ecocore.minions.types.TreeSpeciesData;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parsed view of {@code minions.yml}: global minion progression/upgrade
 * rules, per-type icon/efficiency/purchase-price definitions, the
 * Revisi 9 connector network tuning, Revisi 3 planting spacing, and
 * every per-type target/recipe/rarity list (Revisi 12: minion
 * engines must read these from config, never hardcode them).
 */
public final class MinionsConfig {

    public record MinionDefinition(String displayName, String icon, double baseEfficiency) {
    }

    private final Logger logger;

    private final int maxLevel;
    private final int maxStoragePages;
    private final int maxMinionsPerPlayer;
    private final int baseActiveSlotCount;
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

    private final boolean spacingEnabled;
    private final int defaultTreeSpacing;
    private final int defaultCanopyClearance;
    private final int cropSpacing;
    private final Map<Material, Integer> perTreeSpacing = new HashMap<>();
    private final Map<Material, Integer> perTreeCanopyClearance = new HashMap<>();
    private final Set<Material> require2x2Species = new HashSet<>();
    private final double lumberjackSaplingHarvestChance;

    private final double connectorBaseRange;
    private final double connectorRangePerUpgrade;
    private final int connectorMaxRangeUpgrades;
    private final double connectorUpgradeBaseCost;
    private final double connectorUpgradeCostGrowth;

    private final double storagePageUpgradeBaseCost;
    private final int maxActiveSlotCount;
    private final int activeSlotsPerUpgrade;
    private final double storageSlotUpgradeBaseCost;
    private final double radiusUpgradeBaseCost;
    private final double speedUpgradeBaseCost;
    private final double upgradeCostGrowthPerLevel;

    private final Map<MinionType, MinionWorkMode> workModeOverrides = new EnumMap<>(MinionType.class);
    private final Map<MinionType, Set<Material>> targetBlocks = new EnumMap<>(MinionType.class);
    private final Map<Material, Material> smeltingRecipes = new HashMap<>();
    private final Map<Material, TreeSpeciesData> treeSpeciesData = new HashMap<>();
    private final List<FishRarityTier> fishRarityTiers = new ArrayList<>();

    private final Map<MinionType, MinionDefinition> minionDefinitions = new EnumMap<>(MinionType.class);
    private final Map<MinionType, Double> purchasePrices = new EnumMap<>(MinionType.class);

    private final boolean obstacleAvoidanceEnabled;
    private final String targetSelectionStrategy;
    private final int pathfindingMaxNodes;
    private final int pathfindingRecalculateTicks;


    // REVISED
    private final int connectorMaxDirectDistance;
    private final int connectorMaxRelayDistance;
    private final boolean connectorDebug;

    public MinionsConfig(Logger logger, FileConfiguration config) {
        this.logger = logger;
        this.maxLevel = config.getInt("global.max-level", 50);
        this.maxStoragePages = config.getInt("global.max-storage-pages", 10);
        this.maxMinionsPerPlayer = config.getInt("global.max-minions-per-player", 20);
        this.baseActiveSlotCount = config.getInt("global.base-active-slot-count", 9);
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

        ConfigurationSection farmingSection = config.getConfigurationSection("farming.spacing");
        this.spacingEnabled = farmingSection == null || farmingSection.getBoolean("enabled", true);
        this.defaultTreeSpacing = farmingSection != null ? farmingSection.getInt("default-tree-spacing", 2) : 2;
        this.defaultCanopyClearance = farmingSection != null ? farmingSection.getInt("default-canopy-clearance", 2) : 2;
        this.cropSpacing = farmingSection != null ? farmingSection.getInt("crop-spacing", 1) : 1;
        loadPerTreeSpacing(farmingSection);

        ConfigurationSection lumberjackSection = config.getConfigurationSection("lumberjack");
        this.lumberjackSaplingHarvestChance = lumberjackSection != null
                ? lumberjackSection.getDouble("sapling-harvest-chance", 0.15) : 0.15;

        ConfigurationSection connectorSection = config.getConfigurationSection("connector");
        this.connectorBaseRange = connectorSection != null ? connectorSection.getDouble("base-range", 20.0) : 20.0;
        this.connectorRangePerUpgrade = connectorSection != null ? connectorSection.getDouble("range-per-upgrade", 15.0) : 15.0;
        this.connectorMaxRangeUpgrades = connectorSection != null ? connectorSection.getInt("max-range-upgrades", 10) : 10;
        this.connectorUpgradeBaseCost = connectorSection != null ? connectorSection.getDouble("upgrade-base-cost", 750.0) : 750.0;
        
        // REVISED
        this.connectorMaxDirectDistance = config.getInt("connector.max-direct-distance", 10);
        this.connectorMaxRelayDistance = config.getInt("connector.max-relay-distance", 32);
        this.connectorDebug = config.getBoolean("connector.debug", false);

        // Storage
        this.connectorUpgradeCostGrowth = connectorSection != null ? connectorSection.getDouble("upgrade-cost-growth", 1.4) : 1.4;

        ConfigurationSection upgradesSection = config.getConfigurationSection("upgrades");
        this.storagePageUpgradeBaseCost = upgradesSection != null ? upgradesSection.getDouble("storage-page-base-cost", 500.0) : 500.0;
        this.maxActiveSlotCount = upgradesSection != null ? upgradesSection.getInt("max-active-slot-count", 54) : 54;
        this.activeSlotsPerUpgrade = upgradesSection != null ? upgradesSection.getInt("active-slots-per-upgrade", 9) : 9;
        this.storageSlotUpgradeBaseCost = upgradesSection != null ? upgradesSection.getDouble("storage-slot-base-cost", 150.0) : 150.0;
        this.radiusUpgradeBaseCost = upgradesSection != null ? upgradesSection.getDouble("radius-base-cost", 400.0) : 400.0;
        this.speedUpgradeBaseCost = upgradesSection != null ? upgradesSection.getDouble("speed-base-cost", 500.0) : 500.0;
        this.upgradeCostGrowthPerLevel = upgradesSection != null ? upgradesSection.getDouble("cost-growth-per-level", 1.35) : 1.35;

        loadWorkModeOverrides(config.getConfigurationSection("work-mode"));
        loadTargetBlocks(config.getConfigurationSection("targets"));
        loadSmeltingRecipes(config.getConfigurationSection("smelter.recipes"));
        loadTreeSpecies(config.getConfigurationSection("lumberjack.tree-species"));
        loadFishRarity(config.getConfigurationSection("fisherman.rarity"));

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
                        section.getString("icon", "VILLAGER_SPAWN_EGG"),
                        section.getDouble("base-efficiency", 1.0)
                ));
            }
        }

        ConfigurationSection purchaseSection = config.getConfigurationSection("purchase.prices");
        if (purchaseSection != null) {
            for (String key : purchaseSection.getKeys(false)) {
                MinionType type = MinionType.fromConfigKey(key);
                if (type != null) {
                    purchasePrices.put(type, purchaseSection.getDouble(key, 500.0));
                }
            }
        }

        this.obstacleAvoidanceEnabled = config.getBoolean("ai.obstacle-avoidance-enabled", true);
        this.targetSelectionStrategy = config.getString("ai.target-selection-strategy", "NEAREST_HIGHEST_VALUE");
        this.pathfindingMaxNodes = config.getInt("ai.pathfinding-max-nodes", 200);
        this.pathfindingRecalculateTicks = config.getInt("ai.pathfinding-recalculate-ticks", 40);
    }

    private void loadPerTreeSpacing(ConfigurationSection farmingSection) {
        if (farmingSection == null) {
            return;
        }
        ConfigurationSection perTreeSection = farmingSection.getConfigurationSection("per-tree");
        if (perTreeSection == null) {
            return;
        }
        for (String key : perTreeSection.getKeys(false)) {
            Material material = safeMaterial(key);
            if (material == null) {
                continue;
            }
            ConfigurationSection entry = perTreeSection.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            perTreeSpacing.put(material, entry.getInt("spacing", defaultTreeSpacing));
            perTreeCanopyClearance.put(material, entry.getInt("canopy-clearance", defaultCanopyClearance));
            if (entry.getBoolean("require-2x2", false)) {
                require2x2Species.add(material);
            }
        }
    }

    private void loadWorkModeOverrides(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            MinionType type = MinionType.fromConfigKey(key);
            MinionWorkMode mode = MinionWorkMode.fromConfigKey(section.getString(key));
            if (type != null && mode != null) {
                workModeOverrides.put(type, mode);
            }
        }
    }

    private void loadTargetBlocks(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            MinionType type = MinionType.fromConfigKey(key);
            if (type == null) {
                continue;
            }
            List<String> materialNames = section.getStringList(key);
            Set<Material> materials = new HashSet<>();
            for (String name : materialNames) {
                Material material = safeMaterial(name);
                if (material != null) {
                    materials.add(material);
                }
            }
            targetBlocks.put(type, materials);
        }
    }

    private void loadSmeltingRecipes(ConfigurationSection section) {
        if (section == null) {
            putDefaultRecipe("RAW_IRON", "IRON_INGOT");
            putDefaultRecipe("RAW_GOLD", "GOLD_INGOT");
            putDefaultRecipe("RAW_COPPER", "COPPER_INGOT");
            putDefaultRecipe("IRON_ORE", "IRON_INGOT");
            putDefaultRecipe("GOLD_ORE", "GOLD_INGOT");
            putDefaultRecipe("COPPER_ORE", "COPPER_INGOT");
            putDefaultRecipe("SAND", "GLASS");
            putDefaultRecipe("COBBLESTONE", "STONE");
            return;
        }
        for (String key : section.getKeys(false)) {
            Material input = safeMaterial(key);
            Material output = safeMaterial(section.getString(key));
            if (input != null && output != null) {
                smeltingRecipes.put(input, output);
            } else if (input == null) {
                logger.warning("[EcoCore] minions.yml smelter.recipes: material tidak valid '" + key + "', dilewati.");
            }
        }
    }

    private void putDefaultRecipe(String inputName, String outputName) {
        Material input = safeMaterial(inputName);
        Material output = safeMaterial(outputName);
        if (input != null && output != null) {
            smeltingRecipes.put(input, output);
        }
    }

    private void loadTreeSpecies(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Material log = safeMaterial(entry.getString("log"));
            Material leaves = safeMaterial(entry.getString("leaves"));
            Material sapling = safeMaterial(entry.getString("sapling"));
            if (log == null || leaves == null || sapling == null) {
                logger.warning("[EcoCore] minions.yml lumberjack.tree-species entry '" + key + "' tidak lengkap, dilewati.");
                continue;
            }
            double appleChance = entry.getDouble("apple-chance", 0.0);
            double stickChance = entry.getDouble("stick-chance", 0.02);
            boolean require2x2 = entry.getBoolean("require-2x2", require2x2Species.contains(log));
            treeSpeciesData.put(log, new TreeSpeciesData(log, leaves, sapling, appleChance, stickChance, require2x2, List.of()));
        }
    }

    private void loadFishRarity(ConfigurationSection section) {
        if (section == null) {
            fishRarityTiers.add(new FishRarityTier("COMMON", 70.0, safeMaterialList("COD", "SALMON")));
            fishRarityTiers.add(new FishRarityTier("UNCOMMON", 20.0, safeMaterialList("TROPICAL_FISH", "PUFFERFISH")));
            fishRarityTiers.add(new FishRarityTier("RARE", 8.0, safeMaterialList("NAUTILUS_SHELL")));
            fishRarityTiers.add(new FishRarityTier("EPIC", 1.8, safeMaterialList("HEART_OF_THE_SEA")));
            fishRarityTiers.add(new FishRarityTier("LEGENDARY", 0.2, safeMaterialList("DIAMOND", "EMERALD")));
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            double weight = entry.getDouble("weight", 1.0);
            List<Material> pool = new ArrayList<>();
            for (String materialName : entry.getStringList("pool")) {
                Material material = safeMaterial(materialName);
                if (material != null) {
                    pool.add(material);
                }
            }
            if (!pool.isEmpty()) {
                fishRarityTiers.add(new FishRarityTier(key, weight, pool));
            }
        }
    }

    private List<Material> safeMaterialList(String... names) {
        List<Material> result = new ArrayList<>();
        for (String name : names) {
            Material material = safeMaterial(name);
            if (material != null) {
                result.add(material);
            }
        }
        return result;
    }

    private Material safeMaterial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException invalidMaterial) {
            logger.warning("[EcoCore] minions.yml: material tidak dikenal '" + name + "', dilewati.");
            return null;
        }
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getMaxStoragePages() {
        return maxStoragePages;
    }

    public int getMaxMinionsPerPlayer() {
        return maxMinionsPerPlayer;
    }

    public int getBaseActiveSlotCount() {
        return baseActiveSlotCount;
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

    public boolean isSpacingEnabled() {
        return spacingEnabled;
    }

    public int getCropSpacing() {
        return cropSpacing;
    }

    public int getTreeSpacingFor(Material logMaterial) {
        if (!spacingEnabled) {
            return 0;
        }
        return perTreeSpacing.getOrDefault(logMaterial, defaultTreeSpacing);
    }

    public int getCanopyClearanceFor(Material logMaterial) {
        return perTreeCanopyClearance.getOrDefault(logMaterial, defaultCanopyClearance);
    }

    public double getLumberjackSaplingHarvestChance() {
        return lumberjackSaplingHarvestChance;
    }

    public double getConnectorBaseRange() {
        return connectorBaseRange;
    }

    public double getConnectorRangePerUpgrade() {
        return connectorRangePerUpgrade;
    }

    public int getConnectorMaxRangeUpgrades() {
        return connectorMaxRangeUpgrades;
    }

    public double getConnectorUpgradeBaseCost() {
        return connectorUpgradeBaseCost;
    }

    public double getConnectorUpgradeCostGrowth() {
        return connectorUpgradeCostGrowth;
    }

    public double getStoragePageUpgradeBaseCost() {
        return storagePageUpgradeBaseCost;
    }

    public int getMaxActiveSlotCount() {
        return maxActiveSlotCount;
    }

    public int getActiveSlotsPerUpgrade() {
        return activeSlotsPerUpgrade;
    }

    public double getStorageSlotUpgradeBaseCost() {
        return storageSlotUpgradeBaseCost;
    }

    public double getRadiusUpgradeBaseCost() {
        return radiusUpgradeBaseCost;
    }

    public double getSpeedUpgradeBaseCost() {
        return speedUpgradeBaseCost;
    }

    public double getUpgradeCostGrowthPerLevel() {
        return upgradeCostGrowthPerLevel;
    }

    public MinionWorkMode getWorkModeFor(MinionType type) {
        return workModeOverrides.getOrDefault(type, MinionWorkMode.defaultFor(type));
    }

    public Set<Material> getTargetBlocksFor(MinionType type) {
        return targetBlocks.getOrDefault(type, Set.of());
    }

    public Map<Material, Material> getSmeltingRecipes() {
        return smeltingRecipes;
    }

    public Map<Material, TreeSpeciesData> getTreeSpeciesData() {
        return treeSpeciesData;
    }

    public List<FishRarityTier> getFishRarityTiers() {
        return fishRarityTiers;
    }

    public MinionDefinition getDefinition(MinionType type) {
        return minionDefinitions.get(type);
    }

    public double getPurchasePrice(MinionType type) {
        return purchasePrices.getOrDefault(type, 500.0);
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
    // REVISED
    public int getConnectorMaxDirectDistance() { return connectorMaxDirectDistance; }
    public int getConnectorMaxRelayDistance() { return connectorMaxRelayDistance; }
    public boolean isConnectorDebug() { return connectorDebug; }
}
