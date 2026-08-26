// FILE: src/main/java/io/azthera/ecocore/claim/ClaimManager.java
package io.azthera.ecocore.claim;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Checks whether a minion action (place/break) is allowed at a given
 * location under whichever claim-protection plugin the server is
 * running (Revisi 19).
 *
 * Tries each known {@link ClaimHook} in priority order
 * (GriefPrevention, then WorldGuard, then Lands) at startup and uses
 * the first one whose target plugin is actually present and whose
 * reflection setup succeeds. If none are present, or {@code
 * config.yml claim-protection-enabled} is {@code false}, every check
 * is permissively allowed - this preserves this class's original
 * public method signature exactly (see the earlier minion-core
 * batch's stub), so nothing calling {@code
 * claimManager.isAllowed(...)} needed to change when this real
 * implementation replaced the stub.
 */
public final class ClaimManager {

    private final Logger logger;
    private final boolean claimProtectionEnabled;
    private ClaimHook activeHook;

    /**
     * Creates the claim manager and immediately attempts to detect
     * and initialize a claim plugin hook. Safe to call during plugin
     * {@code onEnable()} - detection failures never throw, they just
     * leave {@link #activeHook} {@code null} (permissive fallback).
     *
     * @param logger plugin logger for detection/warning messages
     * @param claimProtectionEnabled the resolved {@code
     *                                config.yml claim-protection-enabled} value
     */
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

    /**
     * Whether a minion belonging to {@code ownerId} may act (place or
     * break a block) at {@code location}.
     *
     * @param ownerId the minion owner's uuid
     * @param location the location the minion wants to act at
     * @return {@code true} if the action is allowed
     */
    public boolean isAllowed(UUID ownerId, Location location) {
        if (!claimProtectionEnabled || activeHook == null) {
            return true;
        }
        return activeHook.isAllowed(ownerId, location);
    }

    /**
     * The name of the currently active claim plugin integration, for
     * display in {@code /ecocore debug} style diagnostics.
     *
     * @return the active plugin's name, or {@code "none"} if no integration is active
     */
    public String getActiveIntegrationName() {
        if (!claimProtectionEnabled) {
            return "disabled";
        }
        return activeHook != null ? activeHook.targetPluginName() : "none";
    }
}