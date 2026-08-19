package io.azthera.ecocore.hook;

import io.azthera.ecocore.config.BlacklistConfig;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft integration with the Slimefun plugin, resolved via reflection.
 */
public final class SlimefunHook {

    private final boolean available;

    /**
     * Creates the hook, detecting at construction time whether
     * Slimefun is present on the server.
     */
    public SlimefunHook() {
        this.available = Bukkit.getPluginManager().getPlugin("Slimefun") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Resolves the Slimefun item id for an item stack, if any.
     *
     * @param stack the item stack to identify
     * @return the Slimefun item id, or {@code null} if not a Slimefun item or unavailable
     */
    public String resolveId(ItemStack stack) {
        if (!available || stack == null) {
            return null;
        }
        try {
            Class<?> slimefunItemClass = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            Method getByItemMethod = slimefunItemClass.getMethod("getByItem", ItemStack.class);
            Object slimefunItem = getByItemMethod.invoke(null, stack);
            if (slimefunItem == null) {
                return null;
            }
            Method getIdMethod = slimefunItemClass.getMethod("getId");
            return (String) getIdMethod.invoke(slimefunItem);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    /**
     * Checks whether an item stack resolves to a blacklisted Slimefun id.
     *
     * @param stack           the item stack to check
     * @param blacklistConfig resolved blacklist.yml configuration
     * @return {@code true} if blacklisted
     */
    public boolean isBlacklisted(ItemStack stack, BlacklistConfig blacklistConfig) {
        String id = resolveId(stack);
        return id != null && blacklistConfig.getSlimefunBlacklistedIds().contains(id);
    }
}