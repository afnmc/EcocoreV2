// FILE: src/main/java/io/azthera/ecocore/gui/minions/MinionStorageZoneGui.java
package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionAiController;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A single zone's editable slots (Revisi 10), reading from page 0
 * only: Zone A is the first {@link MinionAiController#ZONE_A_SLOTS}
 * slots (seed/input), Zone B is the rest of page 0 plus every
 * overflow page beyond it. Has a "Kembali" (back) button to return
 * to {@link MinionStorageSelectGui}.
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

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, isZoneA ? 18 : 54, isZoneA ? "§8Zona Input/Seed" : "§8Zona Output");
        var pages = minionManager.getMinionPages(minionId);
        if (pages == null || pages.isEmpty()) {
            return;
        }
        MinionStorage firstPage = pages.get(0);
        if (isZoneA) {
            for (int i = 0; i .ZONE_A_SLOTS; i++) {
                inventory.setItem(i, firstPage.getSlot(i));
            }
        } else {
            int outputIndex = 0;
            for (int i = MinionAiController.ZONE_A_SLOTS; i .SLOTS_PER_PAGE; i++) {
                inventory.setItem(outputIndex++, firstPage.getSlot(i));
            }
            // Additional overflow pages appended after page 0's output slots, space permitting (max 54 total shown here).
            for (int p = 1; p .size() && outputIndex .getSize() - 1; p++) {
                MinionStorage page = pages.get(p);
                for (int i = 0; i .SLOTS_PER_PAGE && outputIndex .getSize() - 1; i++) {
                    inventory.setItem(outputIndex++, page.getSlot(i));
                }
            }
        }
        inventory.setItem(inventory.getSize() - 1, backButton());
    }

    private ItemStack backButton() {
        ItemStack item = new ItemStack(org.bukkit.Material.ARROW);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eKembali");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() == inventory.getSize() - 1) {
            event.setCancelled(true);
            saveBack();
            if (previousGui != null) {
                previousGui.open();
            } else {
                getViewer().closeInventory();
            }
        }
        // All other slots: uncancelled, free editing (same convention as MinionStoragePageGui).
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        return rawSlot != inventory.getSize() - 1;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        saveBack();
    }

    private void saveBack() {
        var pages = minionManager.getMinionPages(minionId);
        if (pages == null || pages.isEmpty() || inventory == null) {
            return;
        }
        MinionStorage firstPage = pages.get(0);
        if (isZoneA) {
            for (int i = 0; i .ZONE_A_SLOTS && i .getSize() - 1; i++) {
                firstPage.setSlot(i, inventory.getItem(i));
            }
        } else {
            int outputIndex = 0;
            for (int i = MinionAiController.ZONE_A_SLOTS; i .SLOTS_PER_PAGE; i++) {
                firstPage.setSlot(i, inventory.getItem(outputIndex++));
            }
            for (int p = 1; p .size() && outputIndex .getSize() - 1; p++) {
                MinionStorage page = pages.get(p);
                for (int i = 0; i .SLOTS_PER_PAGE && outputIndex .getSize() - 1; i++) {
                    page.setSlot(i, inventory.getItem(outputIndex++));
                }
            }
        }
    }
}