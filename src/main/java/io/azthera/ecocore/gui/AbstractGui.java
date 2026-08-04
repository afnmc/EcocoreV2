package io.azthera.ecocore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger("EcoCore");

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
     * Called once by {@link #open()}.
     */
    public abstract void build();

    /**
     * Handles a click anywhere within this screen's inventory.
     *
     * @param event the triggering inventory click event
     */
    public abstract void handleClick(InventoryClickEvent event);

    /**
     * Whether a raw inventory slot within this screen allows free
     * multi-slot dragging (placing/spreading an item stack across
     * several slots in one drag). Used by
     * {@code listener.InventoryClickListener}, which cancels any
     * {@code InventoryDragEvent} that touches even one slot for
     * which this returns {@code false}.
     *
     * <p>The default implementation only allows drags confined
     * entirely to the viewer's own bottom inventory (raw slots at or
     * beyond this screen's top inventory size) - i.e. players can
     * always freely rearrange their own inventory, but dragging into
     * the GUI itself is blocked unless a screen explicitly opts in
     * for specific slots (e.g. a deposit/storage area).
     *
     * @param rawSlot the raw slot index touched by the drag
     * @return {@code true} if dragging into this slot is allowed
     */
    public boolean isFreeDragSlot(int rawSlot) {
        return inventory != null && rawSlot >= inventory.getSize();
    }

    /**
     * Called when the viewer closes this screen. Default implementation
     * does nothing; screens holding transient state can override this
     * to clean up or return items.
     *
     * @param event the triggering inventory close event
     */
    public void handleClose(InventoryCloseEvent event) {
        // No-op by default.
    }

    /**
     * Builds this screen and opens it for the viewer.
     *
     * <p>Wraps {@link #build()} in a try-catch: previously, an
     * unexpected exception inside a screen's build logic (a bad
     * config value, an unresolved item, a null reference from a
     * stale reference, etc.) would silently abort {@code open()}
     * before {@code viewer.openInventory(...)} ever ran - the click
     * that triggered navigation would appear to do nothing at all,
     * leaving the player stuck on whatever screen they were already
     * looking at with zero feedback. Now any such failure is reported
     * to the player and logged with a full stack trace, so a broken
     * screen is loud instead of silent.
     */
    public void open() {
        try {
            build();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "[EcoCore] Failed to build GUI screen "
                    + getClass().getSimpleName() + " for " + viewer.getName(), exception);
            viewer.sendMessage("§cTerjadi error saat membuka menu ini. Coba lagi, atau lapor ke admin.");
            return;
        }

        if (inventory == null) {
            LOGGER.severe("[EcoCore] GUI screen " + getClass().getSimpleName()
                    + " finished build() without ever setting an inventory.");
            viewer.sendMessage("§cTerjadi error saat membuka menu ini.");
            return;
        }

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
