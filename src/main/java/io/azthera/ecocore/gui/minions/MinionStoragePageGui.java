package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.List;

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

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Free interaction within the storage page itself.
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        return true;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pageIndex >= pages.size() || inventory == null) {
            return;
        }
        MinionStorage page = pages.get(pageIndex);
        var contents = inventory.getContents();
        for (int i = 0; i < Math.min(contents.length, MinionStorage.SLOTS_PER_PAGE); i++) {
            page.setSlot(i, contents[i]);
        }
    }
}
