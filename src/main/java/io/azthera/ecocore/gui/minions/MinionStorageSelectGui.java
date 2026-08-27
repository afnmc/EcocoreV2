package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class MinionStorageSelectGui extends AbstractGui {

    private static final int ZONE_A_BUTTON_SLOT = 11;
    private static final int ZONE_B_BUTTON_SLOT = 15;
    private static final int BACK_SLOT = 22;

    private final MinionManager minionManager;
    private final long minionId;
    private final AbstractGui previousGui;

    public MinionStorageSelectGui(Player viewer, MinionManager minionManager, long minionId, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionId = minionId;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        MinionData data = minionManager.getMinion(minionId);
        String title = "\u00a78Pilih Storage";
        inventory = Bukkit.createInventory(this, 27, title);
        if (data == null) {
            return;
        }
        String[] labels = zoneLabels(data.getType());
        inventory.setItem(ZONE_A_BUTTON_SLOT, namedItem(Material.HOPPER, "\u00a7e" + labels[0],
                List.of("\u00a77Klik buat buka zona " + labels[0].toLowerCase() + ".")));
        inventory.setItem(ZONE_B_BUTTON_SLOT, namedItem(Material.CHEST, "\u00a7a" + labels[1],
                List.of("\u00a77Klik buat buka zona " + labels[1].toLowerCase() + ".")));
        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, "\u00a7eKembali", List.of()));
    }

    private String[] zoneLabels(MinionType type) {
        return switch (type) {
            case FARMER -> new String[]{"Biji/Seed", "Hasil Panen"};
            case SMELTER, LUMBERJACK -> new String[]{"Input", "Output"};
            default -> new String[]{"Input", "Output"};
        };
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == ZONE_A_BUTTON_SLOT) {
            new MinionStorageZoneGui(getViewer(), minionManager, minionId, true, this).open();
        } else if (slot == ZONE_B_BUTTON_SLOT) {
            new MinionStorageZoneGui(getViewer(), minionManager, minionId, false, this).open();
        } else if (slot == BACK_SLOT) {
            if (previousGui != null) {
                previousGui.open();
            } else {
                getViewer().closeInventory();
            }
        }
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
