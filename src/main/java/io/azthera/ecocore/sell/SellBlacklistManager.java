package io.azthera.ecocore.sell;

import io.azthera.ecocore.config.BlacklistConfig;
import io.azthera.ecocore.hook.ItemIdentityResolver;
import org.bukkit.inventory.ItemStack;

/**
 * Determines whether a given item stack is allowed to be sold to the
 * shop, combining EcoCore's global untradeable-item rules
 * (blacklist.yml) with any per-server sell-specific exclusions.
 * Buying uses catalog tradeability directly ({@code ShopItemRecord.isTradeable()});
 * this class is specifically for the sell side, where a raw
 * inventory {@link ItemStack} - not a catalog entry - is what's
 * being evaluated.
 */
public final class SellBlacklistManager {

    private final BlacklistConfig blacklistConfig;
    private final ItemIdentityResolver identityResolver;

    /**
     * Creates a sell blacklist manager.
     *
     * @param blacklistConfig  resolved blacklist.yml configuration
     * @param identityResolver resolves an ItemStack's identity across vanilla and custom-item plugins
     */
    public SellBlacklistManager(BlacklistConfig blacklistConfig, ItemIdentityResolver identityResolver) {
        this.blacklistConfig = blacklistConfig;
        this.identityResolver = identityResolver;
    }

    /**
     * Checks whether the given item stack is blocked from being sold.
     *
     * @param stack the item stack to check
     * @return {@code true} if the item may NOT be sold
     */
    public boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        return identityResolver.isUntradeable(stack, blacklistConfig);
    }
}