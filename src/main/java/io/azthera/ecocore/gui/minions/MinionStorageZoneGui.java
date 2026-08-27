package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionAiController;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

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
        inventory = Bukkit.createInventory(this, isZoneA ? 18 : 54, isZoneA ? "\u00a78Zona Input/Seed" : "\u00a78Zona Output");
        var pages = minionManager.getMinionPages(minionId);
        if (pages == null || pages.isEmpty()) {
            return;
        }
        MinionStorage firstPage = pages.get(0);
        if (isZoneA) {
            for (int i = 0; i < MinionAiController.ZONE_A_SLOTS; i++) {
                inventory.setItem(i, firstPage.getSlot(i));
            }
        } else {
            int outputIndex = 0;
            for (int i = MinionAiController.ZONE_A_SLOTS; i < MinionStorage.SLOTS_PER_PAGE; i++) {
                inventory.setItem(outputIndex++, firstPage.getSlot(i));
            }
            for (int p = 1; p < pages.size() && outputIndex < inventory.getSize() - 1; p++) {
                MinionStorage page = pages.get(p);
                for (int i = 0; i < MinionStorage.SLOTS_PER_PAGE && outputIndex < inventory.getSize() - 1; i++) {
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
            meta.setDisplayName("\u00a7eKembali");
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
            for (int i = 0; i < MinionAiController.ZONE_A_SLOTS && i < inventory.getSize() - 1; i++) {
                firstPage.setSlot(i, inventory.getItem(i));
            }
        } else {
            int outputIndex = 0;
            for (int i = MinionAiController.ZONE_A_SLOTS; i < MinionStorage.SLOTS_PER_PAGE; i++) {
                firstPage.setSlot(i, inventory.getItem(outputIndex++));
            }
            for (int p = 1; p < pages.size() && outputIndex < inventory.getSize() - 1; p++) {
                MinionStorage page = pages.get(p);
                for (int i = 0; i < MinionStorage.SLOTS_PER_PAGE && outputIndex < inventory.getSize() - 1; i++) {
                    page.setSlot(i, inventory.getItem(outputIndex++));
                }
            }
        }
    }
}
