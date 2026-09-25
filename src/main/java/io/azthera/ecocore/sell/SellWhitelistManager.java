package io.azthera.ecocore.sell;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional per-server sell whitelist: when populated, only shop item
 * ids present in the whitelist can be sold via {@code /sell},
 * {@code Sell All}, or auto-sell, overriding the normal
 * "any tradeable catalog item can be sold" behavior. Empty by
 * default (whitelist disabled, all non-blacklisted catalog items
 * are sellable).
 */
public final class SellWhitelistManager {

    private final Set<String> whitelistedItemIds = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = false;

    /**
     * Whether the whitelist is currently active. When {@code false},
     * every non-blacklisted catalog item is sellable regardless of
     * whitelist contents.
     *
     * @return {@code true} if the whitelist is enforced
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables whitelist enforcement.
     *
     * @param enabled whether to enforce the whitelist
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Adds an item id to the whitelist.
     *
     * @param itemId the shop item id to allow
     */
    public void add(String itemId) {
        whitelistedItemIds.add(itemId);
    }

    /**
     * Removes an item id from the whitelist.
     *
     * @param itemId the shop item id to remove
     */
    public void remove(String itemId) {
        whitelistedItemIds.remove(itemId);
    }

    /**
     * Checks whether an item id is allowed to be sold under the
     * current whitelist state.
     *
     * @param itemId the shop item id to check
     * @return {@code true} if sellable (whitelist disabled, or item is listed)
     */
    public boolean isAllowed(String itemId) {
        return !enabled || whitelistedItemIds.contains(itemId);
    }

    /**
     * Returns an unmodifiable view of the current whitelist contents.
     *
     * @return the whitelisted item ids
     */
    public Set<String> getWhitelistedItemIds() {
        return Collections.unmodifiableSet(whitelistedItemIds);
    }
}