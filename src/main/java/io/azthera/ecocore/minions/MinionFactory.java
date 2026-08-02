package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Builds a freshly-placed minion's initial {@link MinionData} using
 * the base values configured in {@code minions.yml}.
 */
public final class MinionFactory {

    private final MinionsConfig minionsConfig;

    /**
     * Creates a minion factory.
     *
     * @param minionsConfig resolved minions.yml configuration (base stats)
     */
    public MinionFactory(MinionsConfig minionsConfig) {
        this.minionsConfig = minionsConfig;
    }

    /**
     * Builds the initial persistent data for a newly placed minion at
     * level 1 with full starting energy, no fuel, and empty storage.
     * The returned instance has id {@code -1} until persisted by the caller.
     *
     * @param ownerUuid the placing player's uuid
     * @param type      the minion type being placed
     * @param location  the placement location
     * @return the new minion's initial data, not yet persisted
     */
    public MinionData create(UUID ownerUuid, MinionType type, Location location) {
        long now = System.currentTimeMillis();
        return new MinionData(
                -1L, ownerUuid, type,
                1, 0L,
                minionsConfig.getBaseEnergy(), 0,
                location.getWorld() != null ? location.getWorld().getName() : "world",
                location.getX(), location.getY(), location.getZ(),
                minionsConfig.getBaseStorageSlots(),
                minionsConfig.getBaseRadius(),
                minionsConfig.getBaseSpeedTicks(),
                minionsConfig.isAutoRepairEnabled(),
                minionsConfig.isAutoSellEnabled(),
                minionsConfig.isAutoSmeltEnabled(),
                now, now
        );
    }
}