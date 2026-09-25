package io.azthera.ecocore.listener;

import io.azthera.ecocore.manager.PlayerDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Delegates player join handling to {@link PlayerDataManager}: preloads
 * the player's economy account asynchronously and refreshes any stale job missions.
 */
public final class PlayerJoinListener implements Listener {

    private final PlayerDataManager playerDataManager;

    /**
     * Creates the join listener.
     *
     * @param playerDataManager shared player data manager
     */
    public PlayerJoinListener(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            playerDataManager.handlePreLogin(event.getUniqueId(), event.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        playerDataManager.handleJoin(event.getPlayer());
    }
}