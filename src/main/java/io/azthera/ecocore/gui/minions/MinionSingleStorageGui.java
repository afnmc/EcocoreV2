package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Storage screen for every non-{@code STORAGE}, non-dual-zone minion
 * type (miner, fisherman, collector, mob killer, sell, chest) - a
 * single 54-slot page, but only the first {@link
 * MinionData#getActiveSlotCount()} slots are usable. Slots beyond
 * that are shown as a locked barrier icon and reject all clicks and
 * drags, so a player can never manually stash an item somewhere the
 * AI's own storage logic would never scan (which would otherwise
 * strand that item forever - a second dupe-adjacent bug distinct
 * from, but related to, the live-sync fix elsewhere).
 */
public final class MinionSingleStorageGui extends AbstractGui {

    private final MinionManager minionManager;
    private final long minionId;

    public MinionSingleStorageGui(Player viewer, MinionManager minionManager, long minionId) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionId = minionId;
    }

    private int usableSlotCount() {
        MinionData data = minionManager.getMinion(minionId);
        return data != null ? Math.min(data.getActiveSlotCount(), MinionStorage.SLOTS_PER_PAGE) : 0;
    }

    @Override
    public void build() {
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pages.isEmpty()) {
            inventory = Bukkit.createInventory(this, 54, "\u00a78Storage tidak ditemukan");
            return;
        }
        inventory = Bukkit.createInventory(this, 54, "\u00a78Storage");
        render(pages.get(0));
    }

    private void render(MinionStorage page) {
        int usable = usableSlotCount();
        ItemStack[] contents = page.getContents();
        for (int i = 0; i < MinionStorage.SLOTS_PER_PAGE; i++) {
            if (i < usable) {
                inventory.setItem(i, contents[i]);
            } else {
                inventory.setItem(i, lockedIcon());
            }
        }
    }

    private ItemStack lockedIcon() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7cSlot Terkunci");
            meta.setLore(List.of("\u00a77Upgrade storage minion ini buat", "\u00a77membuka slot tambahan."));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < MinionStorage.SLOTS_PER_PAGE && slot >= usableSlotCount()) {
            event.setCancelled(true);
            return;
        }
        // Usable slots: free vanilla interaction, synced live one tick later.
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        int usable = usableSlotCount();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < MinionStorage.SLOTS_PER_PAGE && rawSlot >= usable) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        if (rawSlot < MinionStorage.SLOTS_PER_PAGE) {
            return rawSlot < usableSlotCount();
        }
        return super.isFreeDragSlot(rawSlot);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        syncToLiveStorage();
    }

    private void syncToLiveStorage() {
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pages.isEmpty() || inventory == null) {
            return;
        }
        MinionStorage page = pages.get(0);
        int usable = usableSlotCount();
        for (int i = 0; i < usable; i++) {
            ItemStack item = inventory.getItem(i);
            // Never persist the locked-slot barrier icon itself as real storage content.
            if (item != null && item.getType() == Material.BARRIER && item.getItemMeta() != null
                    && "\u00a7cSlot Terkunci".equals(item.getItemMeta().getDisplayName())) {
                continue;
            }
            page.setSlot(i, item);
        }
    }
}