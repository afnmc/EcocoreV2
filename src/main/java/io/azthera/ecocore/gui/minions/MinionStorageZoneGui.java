package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionAiController;
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
 * A single zone's editable slots for dual-zone types (Smelter/
 * Lumberjack/Farmer): Zone A is the first {@link
 * MinionAiController#ZONE_A_SLOTS} slots (seed/input), Zone B is the
 * rest of that same single page.
 *
 * <p>Bug fixes in this round:
 * <ul>
 *   <li>Live-sync on every click/drag instead of only on close
 *       (matches {@link MinionStoragePageGui}'s fix - was losing
 *       concurrent minion changes and allowing item duplication).</li>
 *   <li>Both zones now respect {@link MinionData#getActiveSlotCount()}:
 *       slots beyond the minion's currently unlocked count are shown
 *       as a locked barrier and reject all interaction, instead of
 *       always exposing the full 9 (Zone A) / 45 (Zone B) slots
 *       regardless of upgrade level - a player could otherwise stash
 *       items where the AI's own storage logic would never look.</li>
 *   <li>Output (Zone B) is now correctly isolated from input (Zone A)
 *       at the {@code MinionAiController} level too - this GUI just
 *       displays whatever's actually in each zone.</li>
 * </ul>
 */
public final class MinionStorageZoneGui extends AbstractGui {

    private static final int BACK_SLOT = 49;

    private final MinionManager minionManager;
    private final long minionId;
    private final boolean isZoneA;
    private final AbstractGui previousGui;

    public MinionStorageZoneGui(Player viewer, MinionManager minionManager, long minionId,
                                 boolean isZoneA, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionId = minionId;
        this.isZoneA = isZoneA;
        this.previousGui = previousGui;
    }

    /** How many slots of THIS zone are currently unlocked, clamped to the zone's own size. */
    private int usableSlotsInZone() {
        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            return 0;
        }
        int activeSlotCount = Math.min(data.getActiveSlotCount(), MinionStorage.SLOTS_PER_PAGE);
        if (isZoneA) {
            return Math.min(activeSlotCount, MinionAiController.ZONE_A_SLOTS);
        }
        int zoneBSize = MinionStorage.SLOTS_PER_PAGE - MinionAiController.ZONE_A_SLOTS;
        return Math.min(Math.max(0, activeSlotCount - MinionAiController.ZONE_A_SLOTS), zoneBSize);
    }

    private int zoneSize() {
        return isZoneA ? MinionAiController.ZONE_A_SLOTS : MinionStorage.SLOTS_PER_PAGE - MinionAiController.ZONE_A_SLOTS;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, isZoneA ? 18 : 54, isZoneA ? "\u00a78Zona Input/Seed" : "\u00a78Zona Output");
        List<MinionStorage> pages = minionManager.getMinionPages(minionId);
        if (pages == null || pages.isEmpty()) {
            return;
        }
        render(pages.get(0));
    }

    private void render(MinionStorage firstPage) {
        int usable = usableSlotsInZone();
        int size = zoneSize();
        int zoneOffset = isZoneA ? 0 : MinionAiController.ZONE_A_SLOTS;
        for (int i = 0; i < size; i++) {
            if (i < usable) {
                inventory.setItem(i, firstPage.getSlot(zoneOffset + i));
            } else {
                inventory.setItem(i, lockedIcon());
            }
        }
        inventory.setItem(inventory.getSize() - 1, backButton());
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

    private boolean isLockedIconStack(ItemStack item) {
        return item != null && item.getType() == Material.BARRIER && item.getItemMeta() != null
                && "\u00a7cSlot Terkunci".equals(item.getItemMeta().getDisplayName());
    }

    private ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7eKembali");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == inventory.getSize() - 1) {
            event.setCancelled(true);
            syncToLiveStorage();
            if (previousGui != null) {
                previousGui.open();
            } else {
                getViewer().closeInventory();
            }
            return;
        }
        if (slot >= 0 && slot < zoneSize() && slot >= usableSlotsInZone()) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        int usable = usableSlotsInZone();
        int size = zoneSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < size && rawSlot >= usable) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(EcoCorePlugin.getInstance(), this::syncToLiveStorage);
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        if (rawSlot == inventory.getSize() - 1) {
            return false;
        }
        if (rawSlot < zoneSize()) {
            return rawSlot < usableSlotsInZone();
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
        MinionStorage firstPage = pages.get(0);
        int usable = usableSlotsInZone();
        int zoneOffset = isZoneA ? 0 : MinionAiController.ZONE_A_SLOTS;
        for (int i = 0; i < usable; i++) {
            ItemStack item = inventory.getItem(i);
            if (isLockedIconStack(item)) {
                continue;
            }
            firstPage.setSlot(zoneOffset + i, item);
        }
    }
}