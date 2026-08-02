package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Exposes a single minion's live storage array as a directly
 * editable inventory: the player can freely drag items in and out.
 * Unlike every other EcoCore screen, clicks here are NOT cancelled,
 * since manipulating the storage contents is the entire point.
 * Changes are written back to the minion's in-memory storage array
 * on close (and picked up by {@code MinionManager.saveAll()} on the
 * next autosave/shutdown).
 */
public final class MinionStorageGui extends AbstractGui {

    private final MinionManager minionManager;
    private final GuiManager guiManager;
    private final long minionId;
    private final AbstractGui previousGui;

    /**
     * Creates the minion storage screen.
     *
     * @param viewer        the viewing player
     * @param minionManager shared minion manager
     * @param guiManager    shared GUI manager
     * @param minionId      the minion's database id
     * @param previousGui   the screen to return to when this one closes
     */
    public MinionStorageGui(Player viewer, MinionManager minionManager, GuiManager guiManager,
                             long minionId, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.guiManager = guiManager;
        this.minionId = minionId;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        ItemStack[] storage = minionManager.getMinionStorage(minionId);
        int slots = storage != null ? storage.length : 9;
        int rows = Math.max(1, (int) Math.ceil(slots / 9.0));

        inventory = Bukkit.createInventory(this, rows * 9, "§8Storage Minion");
        if (storage != null) {
            for (int i = 0; i < storage.length && i < inventory.getSize(); i++) {
                inventory.setItem(i, storage[i]);
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Intentionally not cancelled: the player is meant to freely
        // move items in and out of the minion's storage.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        ItemStack[] storage = minionManager.getMinionStorage(minionId);
        if (storage == null) {
            return;
        }
        for (int i = 0; i < storage.length && i < inventory.getSize(); i++) {
            storage[i] = inventory.getItem(i);
        }
    }
}