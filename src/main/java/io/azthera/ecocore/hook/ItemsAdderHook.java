package io.azthera.ecocore.hook;

import io.azthera.ecocore.config.BlacklistConfig;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Soft integration with the ItemsAdder plugin, resolved entirely via
 * reflection so EcoCore compiles and runs fine whether or not
 * ItemsAdder is installed. Resolves a stack's ItemsAdder namespaced
 * id (if any) and checks it against {@code blacklist.yml}'s
 * {@code hooks.itemsadder.blacklisted-ids} list.
 */
public final class ItemsAdderHook {

    private final boolean available;

    /**
     * Creates the hook, detecting at construction time whether
     * ItemsAdder is present on the server.
     */
    public ItemsAdderHook() {
        this.available = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    /**
     * Whether the ItemsAdder plugin is currently installed and enabled.
     *
     * @return {@code true} if available
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Resolves the ItemsAdder namespaced id for an item stack, if any.
     *
     * @param stack the item stack to identify
     * @return the ItemsAdder id (e.g. "namespace:item_id"), or {@code null} if not an ItemsAdder item or unavailable
     */
    public String resolveId(ItemStack stack) {
        if (!available || stack == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method byItemStackMethod = apiClass.getMethod("byItemStack", ItemStack.class);
            Object customStack = byItemStackMethod.invoke(null, stack);
            if (customStack == null) {
                return null;
            }
            Method getNamespacedIdMethod = apiClass.getMethod("getNamespacedID");
            return (String) getNamespacedIdMethod.invoke(customStack);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    /**
     * Checks whether an item stack resolves to a blacklisted ItemsAdder id.
     *
     * @param stack           the item stack to check
     * @param blacklistConfig resolved blacklist.yml configuration
     * @return {@code true} if blacklisted
     */
    public boolean isBlacklisted(ItemStack stack, BlacklistConfig blacklistConfig) {
        String id = resolveId(stack);
        return id != null && blacklistConfig.getItemsAdderBlacklistedIds().contains(id);
    }
}