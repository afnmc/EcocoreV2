package io.azthera.ecocore.listener;

import io.azthera.ecocore.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;

/**
 * Routes every inventory click, drag, and close event for a player
 * with an EcoCore screen open to {@link GuiManager}, which forwards
 * it to that player's currently registered {@code AbstractGui}.
 * Drag events are cancelled outright for any EcoCore screen, since
 * no EcoCore GUI supports multi-slot dragging (storage screens use
 * single clicks only, matching vanilla shift-click/pickup behavior).
 */
public final class InventoryClickListener implements Listener {

    private final GuiManager guiManager;

    /**
     * Creates the inventory click listener.
     *
     * @param guiManager shared GUI manager
     */
    public InventoryClickListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !guiManager.hasOpenGui(player)) {
            return;
        }
        guiManager.routeClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !guiManager.hasOpenGui(player)) {
            return;
        }
        // MinionStorageGui is the only screen that allows free item movement,
        // and it only needs single-slot clicks; block drag-splitting everywhere.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        guiManager.routeClose(event);
    }
}