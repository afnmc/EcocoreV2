package io.azthera.ecocore.claim;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class ClaimManager {

    private final Logger logger;
    private final boolean claimProtectionEnabled;
    private ClaimHook activeHook;

    public ClaimManager(Logger logger, boolean claimProtectionEnabled) {
        this.logger = logger;
        this.claimProtectionEnabled = claimProtectionEnabled;
        if (claimProtectionEnabled) {
            detectHook();
        } else {
            logger.info("[EcoCore] Claim protection disabled in config.yml - minions can place/break anywhere.");
        }
    }

    private void detectHook() {
        List<ClaimHook> candidates = List.of(
                new GriefPreventionHook(logger),
                new WorldGuardHook(logger),
                new LandsHook(logger)
        );
        for (ClaimHook candidate : candidates) {
            if (org.bukkit.Bukkit.getPluginManager().getPlugin(candidate.targetPluginName()) == null) {
                continue;
            }
            if (candidate.initialize()) {
                this.activeHook = candidate;
                logger.info("[EcoCore] Claim protection active via " + candidate.targetPluginName() + ".");
                return;
            }
        }
        logger.info("[EcoCore] No supported claim plugin detected (GriefPrevention/WorldGuard/Lands) "
                + "- claim protection is a no-op until one is installed.");
    }

    public boolean isAllowed(UUID ownerId, Location location) {
        if (!claimProtectionEnabled || activeHook == null) {
            return true;
        }
        return activeHook.isAllowed(ownerId, location);
    }

    public String getActiveIntegrationName() {
        if (!claimProtectionEnabled) {
            return "disabled";
        }
        return activeHook != null ? activeHook.targetPluginName() : "none";
    }
}