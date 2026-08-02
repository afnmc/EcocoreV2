package io.azthera.ecocore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base class for every EcoCore inventory-based GUI screen. Each
 * screen owns its own {@link Inventory}, knows how to (re)build its
 * own contents, and handles clicks routed to it by {@link GuiManager}
 * via {@code listener.InventoryClickListener}.
 *
 * <p>Screens are single-use: a new instance is created every time a
 * screen is opened (including "going back"), so no screen needs to
 * worry about resetting stale state between views.
 */
public abstract class AbstractGui implements InventoryHolder {

    protected final Player viewer;
    protected Inventory inventory;

    /**
     * Creates a GUI screen bound to the player who will view it.
     *
     * @param viewer the player this screen is being built for
     */
    protected AbstractGui(Player viewer) {
        this.viewer = viewer;
    }

    /**
     * Builds (or rebuilds) this screen's {@link #inventory} contents.
     * Called once by {@link #open()}, and again by screens that
     * refresh in place (e.g. after a sort change).
     */
    public abstract void build();

    /**
     * Handles a click anywhere within this screen's inventory.
     * Implementations are responsible for cancelling the event when
     * the click should not modify the underlying inventory (true for
     * essentially every EcoCore GUI screen).
     *
     * @param event the triggering inventory click event
     */
    public abstract void handleClick(InventoryClickEvent event);

    /**
     * Called when the viewer closes this screen. Default implementation
     * does nothing; screens holding transient state (e.g. a chest sell
     * screen) can override this to clean up.
     *
     * @param event the triggering inventory close event
     */
    public void handleClose(InventoryCloseEvent event) {
        // No-op by default.
    }

    /**
     * Builds this screen and opens it for the viewer.
     */
    public void open() {
        build();
        viewer.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getViewer() {
        return viewer;
    }
}