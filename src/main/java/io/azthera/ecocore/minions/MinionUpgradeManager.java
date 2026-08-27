package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.MinionData;

import java.util.UUID;

/**
 * Handles purchasing minion upgrades (storage pages, radius, speed),
 * enforcing the per-upgrade-type caps from {@code minions.yml} and
 * charging the owner via {@link EconomyEngine}.
 *
 * <p>Revisi 11 replaced the old flat storage-slot-count model with
 * discrete storage pages (1-10, each a full 54-slot page).
 * {@link UpgradeType#STORAGE_PAGE} tracks upgrades by page count
 * directly, and {@link #canUpgrade}/{@link #computeUpgradeCost} are
 * total functions that can never throw or misbehave at the max
 * level (Revisi 14 fix for the old "GUI won't open at max" bug).
 */
public final class MinionUpgradeManager {

    private final MinionsConfig minionsConfig;
    private final EconomyEngine economyEngine;

    public MinionUpgradeManager(MinionsConfig minionsConfig, EconomyEngine economyEngine) {
        this.minionsConfig = minionsConfig;
        this.economyEngine = economyEngine;
    }

    public enum UpgradeType {
        STORAGE_PAGE,
        RADIUS,
        SPEED
    }

    public int getMaxStoragePages() {
        return Math.max(1, minionsConfig.getMaxStoragePages());
    }

    public boolean canUpgrade(MinionData data, UpgradeType type) {
        if (data == null) {
            return false;
        }
        return switch (type) {
            case STORAGE_PAGE -> data.getStoragePageCount() < getMaxStoragePages();
            case RADIUS -> currentRadiusUpgrades(data) < minionsConfig.getMaxRadiusUpgrades();
            case SPEED -> data.getSpeedTicks() > minionsConfig.getMinSpeedTicks();
        };
    }

    public double computeUpgradeCost(MinionData data, UpgradeType type) {
        if (data == null) {
            return 0;
        }
        int currentTier = switch (type) {
            case STORAGE_PAGE -> Math.max(0, data.getStoragePageCount() - 1);
            case RADIUS -> currentRadiusUpgrades(data);
            case SPEED -> currentSpeedUpgrades(data);
        };
        double baseCost = switch (type) {
            case STORAGE_PAGE -> minionsConfig.getStoragePageUpgradeBaseCost();
            case RADIUS -> minionsConfig.getRadiusUpgradeBaseCost();
            case SPEED -> minionsConfig.getSpeedUpgradeBaseCost();
        };
        return baseCost * Math.pow(minionsConfig.getUpgradeCostGrowthPerLevel(), currentTier);
    }

    /**
     * Attempts to purchase and apply the next upgrade of a given type
     * for a minion, charging the owner if eligible and affordable.
     *
     * @param ownerUuid the minion owner's uuid, charged for the upgrade
     * @param data the minion's persistent data, mutated in place if the upgrade succeeds
     * @param type the upgrade type to apply
     * @param minionManager the minion manager, used to actually add a storage page when applicable
     * @return {@code true} if the upgrade was purchased and applied
     */
    public boolean purchaseUpgrade(UUID ownerUuid, MinionData data, UpgradeType type, MinionManager minionManager) {
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
            case STORAGE_PAGE -> minionManager.addStoragePage(data.getId(), getMaxStoragePages());
            case RADIUS -> data.setRadius(data.getRadius() + minionsConfig.getRadiusPerUpgrade());
            case SPEED -> data.setSpeedTicks(Math.max(minionsConfig.getMinSpeedTicks(),
                    data.getSpeedTicks() - minionsConfig.getSpeedReductionPerUpgradeTicks()));
        }
        data.setLevel(computeLevel(data));
        return true;
    }

    public int computeLevel(MinionData data) {
        return 1 + Math.max(0, data.getStoragePageCount() - 1) + currentRadiusUpgrades(data) + currentSpeedUpgrades(data);
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
