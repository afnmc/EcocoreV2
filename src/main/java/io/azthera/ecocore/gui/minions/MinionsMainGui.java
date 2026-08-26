package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The root {@code /minions} screen: lists every minion the player
 * currently owns, plus a button to buy a new one.
 */
public final class MinionsMainGui extends AbstractGui {

    private static final int BUY_SLOT = 4;
    private static final int CLOSE_SLOT = 31;

    private final MinionManager minionManager;
    private final MinionsConfig minionsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    private final Map<Integer, Long> slotToMinionId = new HashMap<>();

    /**
     * Creates the minions main screen.
     *
     * @param viewer        the viewing player
     * @param minionManager shared minion manager
     * @param minionsConfig resolved minions.yml configuration
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public MinionsMainGui(Player viewer, MinionManager minionManager, MinionsConfig minionsConfig,
                           GuiManager guiManager, GuiConfig guiConfig) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionsConfig = minionsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, guiConfig.getMinionsMainRows() * 9, "§8Minions");
        render();
    }

    private void render() {
        slotToMinionId.clear();

        ItemStack buyIcon = new ItemStack(Material.EMERALD);
        ItemMeta buyMeta = buyIcon.getItemMeta();
        if (buyMeta != null) {
            buyMeta.setDisplayName("§a§lBeli Minion Baru");
            buyMeta.setLore(List.of("§7Klik buat lihat semua tipe minion", "§7dan harganya."));
            buyIcon.setItemMeta(buyMeta);
        }
        inventory.setItem(BUY_SLOT, buyIcon);

        List<MinionData> owned = minionManager.getMinionsOwnedBy(viewer.getUniqueId());
        int slot = 9;
        int maxSlots = (guiConfig.getMinionsMainRows() * 9) - 9;

        for (MinionData data : owned) {
            if (slot >= maxSlots) {
                break;
            }
            inventory.setItem(slot, buildMinionIcon(data));
            slotToMinionId.put(slot, data.getId());
            slot++;
        }

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildMinionIcon(MinionData data) {
        boolean fueled = data.getFuelTicksRemaining() > 0;
        ItemStack icon = ItemUtils.buildMinionTypeIcon(data.getType(), minionsConfig);

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + data.getType().configKey() + " §7Lv." + data.getLevel());
            meta.setLore(List.of(
                    "§7Energi: §f" + data.getEnergy() + "/" + minionsConfig.getBaseEnergy(),
                    "§7Fuel: " + (fueled ? "§a§lAktif" : "§c§lHabis"),
                    "§7Radius: §f" + data.getRadius(),
                    "§7Storage: §f" + data.getStorageSlots() + " slot",
                    "§8Klik: Upgrade §8| Shift-klik: Storage"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == BUY_SLOT) {
            MinionsBuyGui buyGui = new MinionsBuyGui(viewer, minionManager, minionsConfig, guiManager, this);
            guiManager.register(viewer, buyGui);
            buyGui.open();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        Long minionId = slotToMinionId.get(slot);
        if (minionId == null) {
            return;
        }

        if (event.isShiftClick()) {
            MinionStorageGui storageGui = new MinionStorageGui(viewer, minionManager, guiManager, minionId, this);
            guiManager.register(viewer, storageGui);
            storageGui.open();
            return;
        }

        MinionUpgradeGui upgradeGui = new MinionUpgradeGui(
                viewer, minionManager, minionsConfig, guiManager, guiConfig, minionId, this);
        guiManager.register(viewer, upgradeGui);
        upgradeGui.open();
    }
                }
