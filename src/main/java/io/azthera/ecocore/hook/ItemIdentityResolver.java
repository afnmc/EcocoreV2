package io.azthera.ecocore.hook;

import io.azthera.ecocore.config.BlacklistConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Central point for deciding whether a given {@link ItemStack} is
 * allowed to be traded, checking vanilla material/namespaced-key/
 * custom-model-data/NBT rules from {@code blacklist.yml} plus every
 * registered custom-item plugin hook. A single "untradeable" persistent
 * data flag (also checked here) lets server owners mark any individual
 * item instance as untradeable regardless of its source.
 */
public final class ItemIdentityResolver {

    private final ItemsAdderHook itemsAdderHook;
    private final OraxenHook oraxenHook;
    private final MMOItemsHook mmoItemsHook;
    private final SlimefunHook slimefunHook;

    /**
     * Creates an item identity resolver.
     *
     * @param itemsAdderHook ItemsAdder integration hook
     * @param oraxenHook     Oraxen integration hook
     * @param mmoItemsHook   MMOItems integration hook
     * @param slimefunHook   Slimefun integration hook
     */
    public ItemIdentityResolver(ItemsAdderHook itemsAdderHook, OraxenHook oraxenHook,
                                 MMOItemsHook mmoItemsHook, SlimefunHook slimefunHook) {
        this.itemsAdderHook = itemsAdderHook;
        this.oraxenHook = oraxenHook;
        this.mmoItemsHook = mmoItemsHook;
        this.slimefunHook = slimefunHook;
    }

    /**
     * Determines whether an item stack is untradeable under any of
     * EcoCore's blacklist rules or any registered custom-item plugin hook.
     *
     * @param stack           the item stack to check
     * @param blacklistConfig resolved blacklist.yml configuration
     * @return {@code true} if the item must NOT be tradeable
     */
    public boolean isUntradeable(ItemStack stack, BlacklistConfig blacklistConfig) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }

        if (blacklistConfig.getMaterials().contains(stack.getType().name())) {
            return true;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (hasUntradeableFlag(meta, blacklistConfig)) {
                return true;
            }
            if (meta.hasCustomModelData()
                    && blacklistConfig.getCustomModelData().contains(meta.getCustomModelData())) {
                return true;
            }
        }

        if (blacklistConfig.isItemsAdderHookEnabled() && itemsAdderHook.isBlacklisted(stack, blacklistConfig)) {
            return true;
        }
        if (blacklistConfig.isOraxenHookEnabled() && oraxenHook.isBlacklisted(stack, blacklistConfig)) {
            return true;
        }
        if (blacklistConfig.isMmoItemsHookEnabled() && mmoItemsHook.isBlacklisted(stack, blacklistConfig)) {
            return true;
        }
        if (blacklistConfig.isSlimefunHookEnabled() && slimefunHook.isBlacklisted(stack, blacklistConfig)) {
            return true;
        }

        return false;
    }

    private boolean hasUntradeableFlag(ItemMeta meta, BlacklistConfig blacklistConfig) {
        String[] parts = blacklistConfig.getPersistentDataKey().split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
        Byte flag = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /**
     * Marks an item stack as permanently untradeable by writing
     * EcoCore's untradeable persistent data flag to it, used by admin
     * tooling that tags event/quest/unique items after the fact.
     *
     * @param stack           the item stack to mark, mutated in place
     * @param blacklistConfig resolved blacklist.yml configuration (for the key to write)
     */
    public void markUntradeable(ItemStack stack, BlacklistConfig blacklistConfig) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        String[] parts = blacklistConfig.getPersistentDataKey().split(":", 2);
        if (parts.length != 2) {
            return;
        }
        NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}