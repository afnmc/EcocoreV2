package io.azthera.ecocore.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class GriefPreventionHook implements ClaimHook {

    private final Logger logger;
    private Object dataStoreInstance;
    private Method getClaimAtMethod;
    private Method getOwnerIdMethod;
    private boolean usable;

    public GriefPreventionHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String targetPluginName() {
        return "GriefPrevention";
    }

    @Override
    public boolean initialize() {
        try {
            Plugin griefPreventionPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (griefPreventionPlugin == null) {
                return false;
            }
            Class<?> griefPreventionClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object instanceField = griefPreventionClass.getField("instance").get(null);
            java.lang.reflect.Field dataStoreField = griefPreventionClass.getField("dataStore");
            dataStoreInstance = dataStoreField.get(instanceField);
            Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            getClaimAtMethod = dataStoreInstance.getClass().getMethod(
                    "getClaimAt", Location.class, boolean.class, claimClass);
            getOwnerIdMethod = claimClass.getMethod("getOwnerID");
            usable = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("[EcoCore] GriefPrevention detected but reflection setup failed ("
                    + exception.getClass().getSimpleName() + "): " + exception.getMessage()
                    + " - claim checks against GriefPrevention will be skipped (always allowed).");
            usable = false;
            return false;
        }
    }

    @Override
    public boolean isAllowed(UUID ownerId, Location location) {
        if (!usable) {
            return true;
        }
        try {
            Object claim = getClaimAtMethod.invoke(dataStoreInstance, location, false, null);
            if (claim == null) {
                return true;
            }
            Object claimOwnerIdRaw = getOwnerIdMethod.invoke(claim);
            if (!(claimOwnerIdRaw instanceof UUID claimOwnerId)) {
                return true;
            }
            return claimOwnerId.equals(ownerId);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("[EcoCore] GriefPrevention claim check failed at runtime, allowing by default: "
                    + exception.getMessage());
            return true;
        }
    }
}
