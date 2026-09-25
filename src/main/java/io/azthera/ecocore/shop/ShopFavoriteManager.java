package io.azthera.ecocore.shop;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each player's favorited shop items for the current server
 * session. Favorites are kept in memory only; they reset on restart.
 * If persistent favorites are needed later, this is the class to
 * back with a new {@code player_favorites} table and DAO.
 */
public final class ShopFavoriteManager {

    private final Map<UUID, Set<String>> favorites = new ConcurrentHashMap<>();

    /**
     * Toggles whether an item is favorited for a player.
     *
     * @param playerUuid the player's uuid
     * @param itemId     the item id to toggle
     * @return {@code true} if the item is now favorited, {@code false} if it was just unfavorited
     */
    public boolean toggle(UUID playerUuid, String itemId) {
        Set<String> set = favorites.computeIfAbsent(playerUuid, key -> ConcurrentHashMap.newKeySet());
        if (set.contains(itemId)) {
            set.remove(itemId);
            return false;
        }
        set.add(itemId);
        return true;
    }

    /**
     * Checks whether a player has favorited a given item.
     *
     * @param playerUuid the player's uuid
     * @param itemId     the item id to check
     * @return {@code true} if favorited
     */
    public boolean isFavorite(UUID playerUuid, String itemId) {
        Set<String> set = favorites.get(playerUuid);
        return set != null && set.contains(itemId);
    }

    /**
     * Returns a player's full set of favorited item ids.
     *
     * @param playerUuid the player's uuid
     * @return an unmodifiable view of the player's favorites, empty if none set
     */
    public Set<String> getFavorites(UUID playerUuid) {
        Set<String> set = favorites.get(playerUuid);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /**
     * Clears a player's favorites from memory, called on player quit
     * to avoid retaining state for offline players indefinitely.
     *
     * @param playerUuid the player's uuid
     */
    public void clear(UUID playerUuid) {
        favorites.remove(playerUuid);
    }
}