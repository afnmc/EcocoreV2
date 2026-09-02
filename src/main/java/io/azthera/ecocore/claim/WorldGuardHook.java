package io.azthera.ecocore.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class WorldGuardHook implements ClaimHook {

    private final Logger logger;
    private Object regionContainerInstance;
    private Method getMethod;
    private Method getApplicableRegionsMethod;
    private Method getRegionsMethod;
    private Method getOwnersMethod;
    private Method getMembersMethod;
    private Method containsMethod;
    private Class<?> bukkitAdapterClass;
    private Method adaptMethod;
    private boolean usable;

    public WorldGuardHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String targetPluginName() {
        return "WorldGuard";
    }

    @Override
    public boolean initialize() {
        try {
            Plugin worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (worldGuardPlugin == null) {
                return false;
            }
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuardInstance = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuardInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            this.regionContainerInstance = regionContainer;

            bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptMethod = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class);

            Class<?> worldClass = Class.forName("com.sk89q.worldedit.world.World");
            getMethod = regionContainer.getClass().getMethod("get", worldClass);

            Class<?> regionManagerClass = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            getApplicableRegionsMethod = regionManagerClass.getMethod(
                    "getApplicableRegions", Class.forName("com.sk89q.worldedit.math.BlockVector3"));

            Class<?> applicableRegionSetClass = Class.forName(
                    "com.sk89q.worldguard.protection.ApplicableRegionSet");
            getRegionsMethod = applicableRegionSetClass.getMethod("getRegions");

            Class<?> protectedRegionClass = Class.forName(
                    "com.sk89q.worldguard.protection.regions.ProtectedRegion");
            getOwnersMethod = protectedRegionClass.getMethod("getOwners");
            getMembersMethod = protectedRegionClass.getMethod("getMembers");

            Class<?> domainClass = Class.forName("com.sk89q.worldguard.domains.DefaultDomain");
            containsMethod = domainClass.getMethod("contains", UUID.class);

            usable = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("[EcoCore] WorldGuard detected but reflection setup failed ("
                    + exception.getClass().getSimpleName() + "): " + exception.getMessage()
                    + " - claim checks against WorldGuard will be skipped (always allowed).");
            usable = false;
            return false;
        }
    }

    @Override
    public boolean isAllowed(UUID ownerId, Location location) {
        if (!usable || location.getWorld() == null) {
            return true;
        }
        try {
            Object adaptedWorld = adaptMethod.invoke(null, location.getWorld());
            Object regionManager = getMethod.invoke(regionContainerInstance, adaptedWorld);
            if (regionManager == null) {
                return true;
            }
            Class<?> blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method atMethod = blockVector3Class.getMethod("at", double.class, double.class, double.class);
            Object blockVector = atMethod.invoke(null, location.getX(), location.getY(), location.getZ());
            Object applicableRegions = getApplicableRegionsMethod.invoke(regionManager, blockVector);
            @SuppressWarnings("unchecked")
            java.util.Set<Object> regions = (java.util.Set<Object>) getRegionsMethod.invoke(applicableRegions);
            if (regions.isEmpty()) {
                return true;
            }
            for (Object region : regions) {
                Object owners = getOwnersMethod.invoke(region);
                Object members = getMembersMethod.invoke(region);
                boolean isOwner = (boolean) containsMethod.invoke(owners, ownerId);
                boolean isMember = (boolean) containsMethod.invoke(members, ownerId);
                if (!isOwner && !isMember) {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("[EcoCore] WorldGuard claim check failed at runtime, allowing by default: "
                    + exception.getMessage());
            return true;
        }
    }
}