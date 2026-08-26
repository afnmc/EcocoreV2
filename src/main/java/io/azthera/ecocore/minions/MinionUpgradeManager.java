// FILE: src/main/java/io/azthera/ecocore/minions/MinionUpgradeManager.java
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
 * Revisi 11 replaced the old flat storage-slot-count model with
 * discrete storage pages (1-10, each a full 54-slot page) - {@link
 * UpgradeType#STORAGE_PAGE} tracks upgrades by page count directly
 * rather than deriving a tier from a slot count, which is what
 * caused the old "max level GUI won't open" bug: a flat slot count
 * could drift out of sync with the configured step size and produce
 * a negative or nonsensical tier. Page count is a plain integer with
 * a hard ceiling, so {@link #canUpgrade} can never miscompute.
 */
public final class MinionUpgradeManager {

    private static final double STORAGE_PAGE_BASE_COST = 500.0;
    private static final double RADIUS_UPGRADE_BASE_COST = 400.0;
    private static final double SPEED_UPGRADE_BASE_COST = 500.0;
    private static final double COST_GROWTH_PER_LEVEL = 1.35;

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

    /**
     * The configured max number of storage pages any minion may unlock (Revisi 11).
     *
     * @return the max storage page count, always at least 1
     */
    public int getMaxStoragePages() {
        return Math.max(1, minionsConfig.getMaxStoragePages());
    }

    /**
     * Whether a minion can still be upgraded in the given dimension.
     * Always a safe, total function - never throws, never returns an
     * inconsistent result at the boundary (this is the fix for
     * Revisi 14's "GUI won't open at max level" bug).
     *
     * @param data the minion's persistent data
     * @param type the upgrade type to check
     * @return {@code true} if another upgrade of this type is available
     */
    public boolean canUpgrade(MinionData data, UpgradeType type) {
        if (data == null) {
            return false;
        }
        return switch (type) {
            case STORAGE_PAGE -> data.getStoragePageCount() getMaxStoragePages();
            case RADIUS -> currentRadiusUpgrades(data) .getMaxRadiusUpgrades();
            case SPEED -> data.getSpeedTicks() > minionsConfig.getMinSpeedTicks();
        };
    }

    /**
     * Computes the cost of the next upgrade of a given type. Safe to
     * call even when {@link #canUpgrade} is {@code false} - simply
     * returns the cost the next tier WOULD have cost, for display
     * purposes; callers must still gate the actual purchase on
     * {@link #canUpgrade}.
     *
     * @param data the minion's persistent data
     * @param type the upgrade type
     * @return the cost, growing geometrically with each successive upgrade
     */
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
            case STORAGE_PAGE -> STORAGE_PAGE_BASE_COST;
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

    /**
     * Computes a minion's level from its total purchased upgrades
     * (storage pages beyond the first + radius + speed tiers
     * combined), starting at level 1 with zero upgrades.
     *
     * @param data the minion's persistent data (after the upgrade has been applied)
     * @return the minion's current level
     */
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