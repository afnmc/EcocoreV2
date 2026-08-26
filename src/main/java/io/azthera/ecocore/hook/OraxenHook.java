package io.azthera.ecocore.hook;

import io.azthera.ecocore.config.BlacklistConfig;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft integration with the Oraxen plugin, resolved via reflection so
 * EcoCore has no hard compile/runtime dependency on it.
 */
public final class OraxenHook {

    private final boolean available;

    /**
     * Creates the hook, detecting at construction time whether Oraxen
     * is present on the server.
     */
    public OraxenHook() {
        this.available = Bukkit.getPluginManager().getPlugin("Oraxen") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Resolves the Oraxen item id for an item stack, if any.
     *
     * @param stack the item stack to identify
     * @return the Oraxen item id, or {@code null} if not an Oraxen item or unavailable
     */
    public String resolveId(ItemStack stack) {
        if (!available || stack == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Method getIdByItemMethod = apiClass.getMethod("getIdByItem", ItemStack.class);
            return (String) getIdByItemMethod.invoke(null, stack);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    /**
     * Checks whether an item stack resolves to a blacklisted Oraxen id.
     *
     * @param stack           the item stack to check
     * @param blacklistConfig resolved blacklist.yml configuration
     * @return {@code true} if blacklisted
     */
    public boolean isBlacklisted(ItemStack stack, BlacklistConfig blacklistConfig) {
        String id = resolveId(stack);
        return id != null && blacklistConfig.getOraxenBlacklistedIds().contains(id);
    }
}