package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;

import java.util.UUID;

/**
 * Handles purchasing minion upgrades (storage, radius, speed),
 * enforcing the per-upgrade-type caps from {@code minions.yml} and
 * charging the owner via {@link EconomyEngine}.
 *
 * <p>Bug-fix round: storage upgrades are now split by minion type.
 * {@link UpgradeType#STORAGE_PAGE} (adding a whole 54-slot page, up
 * to 10) is valid ONLY for {@link MinionType#STORAGE} - every other
 * type instead uses {@link UpgradeType#STORAGE_SLOTS}, which
 * unlocks more of its single page's 54 slots a few at a time
 * (starting from a small active count) rather than adding pages.
 * {@link #canUpgrade}/{@link #computeUpgradeCost} remain total
 * functions that can never throw or misbehave at the max level
 * (Revisi 14).
 */
public final class MinionUpgradeManager {

    private final MinionsConfig minionsConfig;
    private final EconomyEngine economyEngine;

    public MinionUpgradeManager(MinionsConfig minionsConfig, EconomyEngine economyEngine) {
        this.minionsConfig = minionsConfig;
        this.economyEngine = economyEngine;
    }

    public enum UpgradeType {
        /** Adds a whole 54-slot storage page. Valid ONLY for {@link MinionType#STORAGE}. */
        STORAGE_PAGE,
        /** Unlocks more usable slots within the single page every other minion type has. */
        STORAGE_SLOTS,
        RADIUS,
        SPEED
    }

    public int getMaxStoragePages() {
        return Math.max(1, minionsConfig.getMaxStoragePages());
    }

    public int getMaxActiveSlotCount() {
        return Math.max(1, minionsConfig.getMaxActiveSlotCount());
    }

    public boolean canUpgrade(MinionData data, UpgradeType type) {
        if (data == null) {
            return false;
        }
        return switch (type) {
            case STORAGE_PAGE -> data.getType() == MinionType.STORAGE
                    && data.getStoragePageCount() < getMaxStoragePages();
            case STORAGE_SLOTS -> data.getType() != MinionType.STORAGE
                    && data.getActiveSlotCount() < getMaxActiveSlotCount();
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
            case STORAGE_SLOTS -> currentSlotUpgrades(data);
            case RADIUS -> currentRadiusUpgrades(data);
            case SPEED -> currentSpeedUpgrades(data);
        };
        double baseCost = switch (type) {
            case STORAGE_PAGE -> minionsConfig.getStoragePageUpgradeBaseCost();
            case STORAGE_SLOTS -> minionsConfig.getStorageSlotUpgradeBaseCost();
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
            case STORAGE_SLOTS -> data.setActiveSlotCount(Math.min(getMaxActiveSlotCount(),
                    data.getActiveSlotCount() + minionsConfig.getActiveSlotsPerUpgrade()));
            case RADIUS -> data.setRadius(data.getRadius() + minionsConfig.getRadiusPerUpgrade());
            case SPEED -> data.setSpeedTicks(Math.max(minionsConfig.getMinSpeedTicks(),
                    data.getSpeedTicks() - minionsConfig.getSpeedReductionPerUpgradeTicks()));
        }
        data.setLevel(computeLevel(data));
        return true;
    }

    public int computeLevel(MinionData data) {
        int storageProgress = data.getType() == MinionType.STORAGE
                ? Math.max(0, data.getStoragePageCount() - 1)
                : currentSlotUpgrades(data);
        return 1 + storageProgress + currentRadiusUpgrades(data) + currentSpeedUpgrades(data);
    }

    private int currentSlotUpgrades(MinionData data) {
        int base = minionsConfig.getBaseActiveSlotCount();
        int step = minionsConfig.getActiveSlotsPerUpgrade();
        return step > 0 ? Math.max(0, (data.getActiveSlotCount() - base) / step) : 0;
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
