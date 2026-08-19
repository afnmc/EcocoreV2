package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.MinionData;

import java.util.UUID;

/**
 * Handles purchasing minion upgrades (storage, radius, speed),
 * enforcing the per-upgrade-type caps from {@code minions.yml} and
 * charging the owner via {@link EconomyEngine}.
 */
public final class MinionUpgradeManager {

    private static final double STORAGE_UPGRADE_BASE_COST = 250.0;
    private static final double RADIUS_UPGRADE_BASE_COST = 400.0;
    private static final double SPEED_UPGRADE_BASE_COST = 500.0;
    private static final double COST_GROWTH_PER_LEVEL = 1.35;

    private final MinionsConfig minionsConfig;
    private final EconomyEngine economyEngine;

    /**
     * Creates an upgrade manager.
     *
     * @param minionsConfig resolved minions.yml configuration (upgrade caps/steps)
     * @param economyEngine economy engine used to charge the owner for upgrades
     */
    public MinionUpgradeManager(MinionsConfig minionsConfig, EconomyEngine economyEngine) {
        this.minionsConfig = minionsConfig;
        this.economyEngine = economyEngine;
    }

    /**
     * The kind of upgrade being purchased.
     */
    public enum UpgradeType {
        STORAGE,
        RADIUS,
        SPEED
    }

    /**
     * Whether a minion can still be upgraded in the given dimension.
     *
     * @param data the minion's persistent data
     * @param type the upgrade type to check
     * @return {@code true} if another upgrade of this type is available
     */
    public boolean canUpgrade(MinionData data, UpgradeType type) {
        return switch (type) {
            case STORAGE -> currentStorageUpgrades(data) < minionsConfig.getMaxStorageUpgrades();
            case RADIUS -> currentRadiusUpgrades(data) < minionsConfig.getMaxRadiusUpgrades();
            case SPEED -> data.getSpeedTicks() > minionsConfig.getMinSpeedTicks();
        };
    }

    /**
     * Computes the cost of the next upgrade of a given type.
     *
     * @param data the minion's persistent data
     * @param type the upgrade type
     * @return the cost, growing geometrically with each successive upgrade
     */
    public double computeUpgradeCost(MinionData data, UpgradeType type) {
        int currentTier = switch (type) {
            case STORAGE -> currentStorageUpgrades(data);
            case RADIUS -> currentRadiusUpgrades(data);
            case SPEED -> currentSpeedUpgrades(data);
        };
        double baseCost = switch (type) {
            case STORAGE -> STORAGE_UPGRADE_BASE_COST;
            case RADIUS -> RADIUS_UPGRADE_BASE_COST;
            case SPEED -> SPEED_UPGRADE_BASE_COST;
        };
        return baseCost * Math.pow(COST_GROWTH_PER_LEVEL, currentTier);
    }

    /**
     * Attempts to purchase and apply the next upgrade of a given type
     * for a minion, charging the owner if eligible and affordable.
     *
     * @param ownerUuid the minion owner's uuid, charged for the upgrade
     * @param data      the minion's persistent data, mutated in place if the upgrade succeeds
     * @param type      the upgrade type to apply
     * @return {@code true} if the upgrade was purchased and applied
     */
    public boolean purchaseUpgrade(UUID ownerUuid, MinionData data, UpgradeType type) {
        if (!canUpgrade(data, type)) {
            return false;
        }

        double cost = computeUpgradeCost(data, type);
        if (!economyEngine.has(ownerUuid, cost)) {
            return false;
        }
        if (!economyEngine.withdraw(ownerUuid, cost, TransactionLogger.REASON_ADMIN_ADJUST)) {
            return false;
        }

        switch (type) {
            case STORAGE -> data.setStorageSlots(data.getStorageSlots() + minionsConfig.getStorageSlotsPerUpgrade());
            case RADIUS -> data.setRadius(data.getRadius() + minionsConfig.getRadiusPerUpgrade());
            case SPEED -> data.setSpeedTicks(Math.max(minionsConfig.getMinSpeedTicks(),
                    data.getSpeedTicks() - minionsConfig.getSpeedReductionPerUpgradeTicks()));
        }

        data.setLevel(computeLevel(data));

        return true;
    }

    /**
     * Computes a minion's level from its total purchased upgrades
     * (storage + radius + speed tiers combined), starting at level 1
     * with zero upgrades. Called immediately after every successful
     * upgrade so the level field always reflects total progress.
     *
     * @param data the minion's persistent data (after the upgrade has been applied)
     * @return the minion's current level
     */
    public int computeLevel(MinionData data) {
        return 1 + currentStorageUpgrades(data) + currentRadiusUpgrades(data) + currentSpeedUpgrades(data);
    }

    private int currentStorageUpgrades(MinionData data) {
        int base = minionsConfig.getBaseStorageSlots();
        int step = minionsConfig.getStorageSlotsPerUpgrade();
        return step > 0 ? Math.max(0, (data.getStorageSlots() - base) / step) : 0;
    }

    private int currentRadiusUpgrades(MinionData data) {
        int base = minionsConfig.getBaseRadius();
        int step = minionsConfig.getRadiusPerUpgrade();
        return step > 0 ? Math.max(0, (data.getRadius() - base) / step) : 0;
    }

    private int currentSpeedUpgrades(MinionData data) {
        int base = minionsConfig.getBaseSpeedTicks();
        int step = minionsConfig.getSpeedReductionPerUpgradeTicks();
        return step > 0 ? Math.max(0, (base - data.getSpeedTicks()) / step) : 0;
    }
}
