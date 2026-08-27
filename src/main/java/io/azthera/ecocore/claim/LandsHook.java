package io.azthera.ecocore.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class LandsHook implements ClaimHook {

    private final Logger logger;
    private Object landsIntegrationInstance;
    private Method getAreaByLocMethod;
    private Method getLandMethod;
    private Method isOwnerMethod;
    private Method isTrustedMethod;
    private boolean usable;

    public LandsHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String targetPluginName() {
        return "Lands";
    }

    @Override
    public boolean initialize() {
        try {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin == null) {
                return false;
            }
            Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            Class<?> javaPluginClass = Class.forName("org.bukkit.plugin.java.JavaPlugin");
            java.lang.reflect.Constructor<?> constructor = landsIntegrationClass.getConstructor(javaPluginClass);
            landsIntegrationInstance = constructor.newInstance((Object) io.azthera.ecocore.EcoCorePlugin.getInstance());

            getAreaByLocMethod = landsIntegrationClass.getMethod("getAreaByLoc", Location.class);
            Class<?> areaClass = Class.forName("me.angeschossen.lands.api.land.Area");
            getLandMethod = areaClass.getMethod("getLand");
            Class<?> landClass = Class.forName("me.angeschossen.lands.api.land.Land");
            isOwnerMethod = landClass.getMethod("isOwner", UUID.class);
            isTrustedMethod = landClass.getMethod("isTrusted", UUID.class);

            usable = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("[EcoCore] Lands detected but reflection setup failed ("
                    + exception.getClass().getSimpleName() + "): " + exception.getMessage()
                    + " - claim checks against Lands will be skipped (always allowed).");
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
            Object area = getAreaByLocMethod.invoke(landsIntegrationInstance, location);
            if (area == null) {
                return true;
            }
            Object land = getLandMethod.invoke(area);
            if (land == null) {
                return true;
            }
            boolean isOwner = (boolean) isOwnerMethod.invoke(land, ownerId);
            boolean isTrusted = (boolean) isTrustedMethod.invoke(land, ownerId);
            return isOwner || isTrusted;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("[EcoCore] Lands claim check failed at runtime, allowing by default: "
                    + exception.getMessage());
            return true;
        }
    }
}
