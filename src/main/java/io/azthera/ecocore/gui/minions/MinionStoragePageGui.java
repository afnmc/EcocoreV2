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
import org.bukkit.inventory.ItemStack;
import java.util.List;

public final class MinionStoragePageGui extends AbstractGui {
    private final MinionManager minionManager;
    private final long minionId;
    private final int pageIndex;
    private ItemStack[] renderedSnapshot;

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
            inventory = Bukkit.createInventory(this, 54, "§8Storage tidak ditemukan");
            renderedSnapshot = new ItemStack[0];
            return;
        }
        MinionStorage page = pages.get(pageIndex);
        inventory = Bukkit.createInventory(this, 54, "§8Storage " + (pageIndex + 1));
        inventory.setContents(page.getContents().clone());
        renderedSnapshot = deepClone(inventory.getContents());
    }

    private MinionStorage resolveLivePage() {
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        return (pages != null && pageIndex < pages.size()) ? pages.get(pageIndex) : null;
    }

    private static ItemStack[] deepClone(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private static boolean isSame(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.isSimilar(b) && a.getAmount() == b.getAmount();
    }

    private void syncToLiveStorage() {
        MinionStorage page = resolveLivePage();
        if (page == null || inventory == null || renderedSnapshot == null) return;
        
        int slots = Math.min(inventory.getSize(), renderedSnapshot.length);
        for (int i = 0; i < slots; i++) {
            ItemStack guiItem = inventory.getItem(i);
            if (isSame(guiItem, renderedSnapshot[i])) {
                // Player didn't touch this slot -> Update GUI from Live State (Minion might have added items)
                ItemStack live = page.getSlot(i);
                inventory.setItem(i, live == null ? null : live.clone());
                renderedSnapshot[i] = live == null ? null : live.clone();
            } else {
                // Player changed this slot -> Update Live State from GUI
                page.setSlot(i, guiItem == null ? null : guiItem.clone());
                renderedSnapshot[i] = guiItem == null ? null : guiItem.clone();
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) { return true; }

    @Override
    public void handleClose(InventoryCloseEvent event) { syncToLiveStorage(); }
}