package io.azthera.ecocore.sell;

import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.ShopItemRecord;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks which players have auto-sell enabled and provides the entry
 * point used by block-break/mob-kill/fishing listeners (and minion
 * auto-sell) to immediately liquidate drops instead of putting them
 * in an inventory. Auto-sell state is in-memory only and defaults to
 * off; it resets on server restart.
 */
public final class AutoSellManager {

    private final Logger logger;
    private final SellManager sellManager;
    private final EconomyEngine economyEngine;

    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Creates an auto-sell manager.
     *
     * @param logger        plugin logger
     * @param sellManager   the sell manager used to price and record sales
     * @param economyEngine economy engine used to pay players directly for auto-sold drops
     */
    public AutoSellManager(Logger logger, SellManager sellManager, EconomyEngine economyEngine) {
        this.logger = logger;
        this.sellManager = sellManager;
        this.economyEngine = economyEngine;
    }

    /**
     * Toggles auto-sell for a player.
     *
     * @param playerUuid the player's uuid
     * @return {@code true} if auto-sell is now enabled
     */
    public boolean toggle(UUID playerUuid) {
        if (enabledPlayers.contains(playerUuid)) {
            enabledPlayers.remove(playerUuid);
            return false;
        }
        enabledPlayers.add(playerUuid);
        return true;
    }

    /**
     * Checks whether auto-sell is currently enabled for a player.
     *
     * @param playerUuid the player's uuid
     * @return {@code true} if enabled
     */
    public boolean isEnabled(UUID playerUuid) {
        return enabledPlayers.contains(playerUuid);
    }

    /**
     * Attempts to immediately sell a single drop for a player with
     * auto-sell enabled. Silently does nothing (returns 0) if the
     * item isn't sellable, so callers (listeners) can call this
     * unconditionally on every drop without extra checks.
     *
     * @param playerUuid the player's uuid
     * @param drop       the dropped item stack to attempt to sell
     * @return the payout received, 0.0 if nothing was sold
     */
    public double attemptAutoSell(UUID playerUuid, ItemStack drop) {
        if (!isEnabled(playerUuid) || drop == null) {
            return 0.0;
        }
        if (!sellManager.isSellable(drop)) {
            return 0.0;
        }

        ShopItemRecord catalogItem = sellManager.resolveCatalogItem(drop);
        if (catalogItem == null) {
            return 0.0;
        }

        SellManager.SellResult result = sellManager.sellSingle(playerUuid, drop);
        return result.success() ? result.totalPayout() : 0.0;
    }

    /**
     * Clears a player's auto-sell state from memory, called on quit.
     *
     * @param playerUuid the player's uuid
     */
    public void clear(UUID playerUuid) {
        enabledPlayers.remove(playerUuid);
    }

    /**
     * Returns an unmodifiable view of every player currently known to
     * have auto-sell enabled, used by {@code /ecocore debug}.
     *
     * @return the set of player uuids with auto-sell on
     */
    public Set<UUID> getEnabledPlayers() {
        return Collections.unmodifiableSet(enabledPlayers);
    }
}