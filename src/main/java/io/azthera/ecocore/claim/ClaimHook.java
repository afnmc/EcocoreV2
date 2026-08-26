// FILE: src/main/java/io/azthera/ecocore/claim/ClaimHook.java
package io.azthera.ecocore.claim;

import org.bukkit.Location;

import java.util.UUID;

/**
 * A single claim-protection plugin integration. Every implementation
 * talks to its target plugin purely via reflection (Revisi 19) so
 * EcoCore never needs that plugin's JAR on the compile classpath -
 * {@code io.papermc.paper.plugin.bootstrap} style optional
 * integrations aren't used here since GriefPrevention/WorldGuard/
 * Lands don't ship as Paper bootstrap-loadable modules; a soft
 * dependency plus reflection is the safe, always-compiles approach.
 *
 * Every method here must be total and defensive: if the target
 * plugin's API doesn't match what reflection expects (wrong method
 * name, changed signature, different return type across versions),
 * the implementation must catch the failure and fall back to
 * "allowed" rather than throwing - a broken claim check should never
 * crash a minion's tick or take down the server, and erring toward
 * "allowed" on integration failure is safer for gameplay continuity
 * than erring toward "denied" (which would silently freeze every
 * minion on the server the moment reflection breaks).
 */
public interface ClaimHook {

    /**
     * The exact plugin name this hook targets, matched against
     * {@code Bukkit.getPluginManager().getPlugin(name)}.
     *
     * @return the target plugin's name as it appears in its own plugin.yml
     */
    String targetPluginName();

    /**
     * Attempts to initialize this hook against the currently loaded
     * instance of its target plugin. Called once when {@link
     * ClaimManager} detects the plugin is present.
     *
     * @return {@code true} if initialization succeeded and this hook is usable
     */
    boolean initialize();

    /**
     * Checks whether {@code ownerId} is allowed to build/break at
     * {@code location} under this claim plugin's rules.
     *
     * @param ownerId the minion owner's uuid
     * @param location the location being acted on
     * @return {@code true} if the action should be allowed
     */
    boolean isAllowed(UUID ownerId, Location location);
}