// FILE: src/main/java/io/azthera/ecocore/gui/minions/MinionStoragePageGui.java
package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.List;

/**
 * A single 54-slot storage page's raw contents, directly editable by
 * the player (Revisi 11). Changes write straight back into the live
 * {@link MinionStorage} object shared with {@code MinionAiController}.
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
        ListMinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pageIndex >= pages.size()) {
            inventory = Bukkit.createInventory(this, 54, "§8Storage tidak ditemukan");
            return;
        }
        MinionStorage page = pages.get(pageIndex);
        inventory = Bukkit.createInventory(this, 54, "§8Storage " + (pageIndex + 1));
        inventory.setContents(page.getContents().clone());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Free interaction within the storage page itself - only clicks that
        // would move items out into the player's own inventory or vice versa
        // need no special handling since both sides are ordinary slots here.
        // Nothing to cancel; vanilla click behavior is correct for a plain
        // player-editable container.
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        // The whole storage page allows free drag placement, plus the player's own inventory.
        return true;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        ListMinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pageIndex >= pages.size() || inventory == null) {
            return;
        }
        MinionStorage page = pages.get(pageIndex);
        page.getContents();
        var contents = inventory.getContents();
        for (int i = 0; i .min(contents.length, MinionStorage.SLOTS_PER_PAGE); i++) {
            page.setSlot(i, contents[i]);
        }
    }
}