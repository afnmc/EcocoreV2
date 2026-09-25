package io.azthera.ecocore.listener;

import io.azthera.ecocore.manager.PlayerDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Delegates player quit handling to {@link PlayerDataManager}: saves
 * and unloads the player's economy account and clears session-only
 * state (shop favorites, auto-sell toggle) from memory.
 */
public final class PlayerQuitListener implements Listener {

    private final PlayerDataManager playerDataManager;

    /**
     * Creates the quit listener.
     *
     * @param playerDataManager shared player data manager
     */
    public PlayerQuitListener(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playerDataManager.handleQuit(event.getPlayer());
    }
}