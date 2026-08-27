package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.minions.MinionUpgradeManager;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

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
            inventory = Bukkit.createInventory(this, 27, "\u00a78Storage tidak ditemukan");
            return;
        }
        inventory = Bukkit.createInventory(this, 27, "\u00a78Pilih Storage - " + data.getType().configKey());
        int pageCount = data.getStoragePageCount();
        int maxPages = upgradeManager.getMaxStoragePages();
        for (int i = 0; i < maxPages && i < 10; i++) {
            boolean unlocked = i < pageCount;
            ItemStack icon = unlocked
                    ? namedItem(Material.CHEST, "\u00a7aStorage " + (i + 1), List.of("\u00a77Klik buat buka storage ini"))
                    : namedItem(Material.BARRIER, "\u00a77Storage " + (i + 1) + " \u00a78(Terkunci)",
                            List.of("\u00a77Upgrade minion buat membuka storage ini"));
            inventory.setItem(i, icon);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 10) {
            return;
        }
        MinionData data = minionManager.getMinion(minionId);
        if (data == null || slot >= data.getStoragePageCount()) {
            return;
        }
        new MinionStoragePageGui(getViewer(), minionManager, minionId, slot).open();
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
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
