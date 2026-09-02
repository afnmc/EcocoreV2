package io.azthera.ecocore.claim;

import org.bukkit.Location;

import java.util.UUID;

public interface ClaimHook {

    String targetPluginName();

    boolean initialize();

    boolean isAllowed(UUID ownerId, Location location);
}