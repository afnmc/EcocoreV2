package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

/**
 * A single 54-slot storage page's raw contents, directly editable by
 * the player (Revisi 11).
 *
 * <p>BUG FIX: this used to snapshot the page's contents into the GUI
 * on open and only write back on close. Since {@code
 * MinionAiController} keeps modifying the LIVE {@link MinionStorage}
 * object in the background every tick while the GUI is open, that
 * old approach silently lost whatever the minion added/removed
 * during the visit, and could let a player duplicate an item (take
 * it out of the GUI, but the live storage still "has" it until
 * close, so the minion could act on it too). Every click and drag is
 * now synced back into the live {@link MinionStorage} one tick after
 * Bukkit finishes applying it, so the two are never out of sync for
 * more than a single tick and never resolved by a stale overwrite.
 */
public final class MinionStoragePageGui extends AbstractGui {

    private final MinionManager minionManager;
    private final long minionId;
    private final int pageIndex;

    public MinionStoragePageGui(Player viewer, MinionManager minionManager, long minionId, int pageIndex) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionId = minionId;
        this.pageIndex = pageIndex;
    }

    @Override
    public void build() {
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pageIndex >= pages.size()) {
            inventory = Bukkit.createInventory(this, 54, "\u00a78Storage tidak ditemukan");
            return;
        }
        MinionStorage page = pages.get(pageIndex);
        inventory = Bukkit.createInventory(this, 54, "\u00a78Storage " + (pageIndex + 1));
        inventory.setContents(page.getContents().clone());
    }

    private MinionStorage resolveLivePage() {
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pageIndex >= pages.size()) {
            return null;
        }
        return pages.get(pageIndex);
    }

    /**
     * Writes the GUI's current contents back into the live {@link
     * MinionStorage} immediately. Safe to call from the main thread
     * synchronously (Bukkit is single-threaded, so there's no real
     * race with the minion tick - only staleness if this were
     * deferred, which is exactly the bug this fixes).
     */
    private void syncToLiveStorage() {
        MinionStorage page = resolveLivePage();
        if (page == null || inventory == null) {
            return;
        }
        var contents = inventory.getContents();
        for (int i = 0; i < Math.min(contents.length, MinionStorage.SLOTS_PER_PAGE); i++) {
            page.setSlot(i, contents[i]);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Free vanilla interaction within the storage page - not
        // cancelled, so click/shift-click/number-key/double-click/
        // offhand all work like a normal chest. Sync happens one tick
        // later so Bukkit has already applied the click's result to
        // `inventory` before we read it back into the live storage.
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        return true;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        syncToLiveStorage();
    }
}
