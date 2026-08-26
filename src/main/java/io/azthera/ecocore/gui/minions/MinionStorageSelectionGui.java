// FILE: src/main/java/io/azthera/ecocore/gui/minions/MinionStorageSelectionGui.java
package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.minions.MinionUpgradeManager;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Sit+click storage page picker (Revisi 11): shows one button per
 * unlocked storage page (Storage 1 up to however many pages the
 * minion has purchased, max 10), each opening that page's full
 * 54-slot inventory. This is the entry point for minion types with
 * only one storage concept (Miner, Fisherman, Collector, Quarry,
 * Mob Killer) - types with a real input/output split (Smelter,
 * Lumberjack, Farmer) go through {@link MinionStorageSelectGui}
 * first instead (Revisi 10).
 */
public final class MinionStorageSelectionGui extends AbstractGui {

    private final MinionManager minionManager;
    private final MinionUpgradeManager upgradeManager;
    private final long minionId;

    public MinionStorageSelectionGui(Player viewer, MinionManager minionManager,
                                      MinionUpgradeManager upgradeManager, long minionId) {
        super(viewer);
        this.minionManager = minionManager;
        this.upgradeManager = upgradeManager;
        this.minionId = minionId;
    }

    @Override
    public void build() {
        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            inventory = Bukkit.createInventory(this, 27, "§8Storage tidak ditemukan");
            return;
        }
        inventory = Bukkit.createInventory(this, 27, "§8Pilih Storage - " + data.getType().configKey());
        int pageCount = data.getStoragePageCount();
        int maxPages = upgradeManager.getMaxStoragePages();
        for (int i = 0; i 10; i++) {
            boolean unlocked = i ;
            ItemStack icon = unlocked
                    ? namedItem(Material.CHEST, "§aStorage " + (i + 1), List.of("§7Klik buat buka storage ini"))
                    : namedItem(Material.BARRIER, "§7Storage " + (i + 1) + " §8(Terkunci)",
                            List.of("§7Upgrade minion buat membuka storage ini"));
            inventory.setItem(i, icon);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot 0 || slot >= 10) {
            return;
        }
        MinionData data = minionManager.getMinion(minionId);
        if (data == null || slot >= data.getStoragePageCount()) {
            return;
        }
        new MinionStoragePageGui(getViewer(), minionManager, minionId, slot).open();
    }

    private ItemStack namedItem(Material material, String name, ListString> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}