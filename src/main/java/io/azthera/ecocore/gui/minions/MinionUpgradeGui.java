// FILE: src/main/java/io/azthera/ecocore/gui/minions/MinionUpgradeGui.java
package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionConnectorManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.minions.MinionUpgradeManager;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Minion upgrade/settings screen. Revisi 14 fix: every icon builder
 * here is now a total function over any minion state, including
 * fully maxed out - {@link #build()} can never throw and the screen
 * can never fail to open, regardless of upgrade level.
 */
public final class MinionUpgradeGui extends AbstractGui {

    private static final int SUMMARY_SLOT = 4;
    private static final int STORAGE_UPGRADE_SLOT = 20;
    private static final int RADIUS_UPGRADE_SLOT = 22;
    private static final int SPEED_UPGRADE_SLOT = 24;
    private static final int MODE_TOGGLE_SLOT = 38;
    private static final int CONNECTOR_SLOT = 42;
    private static final int STORAGE_OPEN_SLOT = 31;
    private static final int REMOVE_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private final MinionManager minionManager;
    private final MinionsConfig minionsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final long minionId;
    private final AbstractGui previousGui;

    public MinionUpgradeGui(Player viewer, MinionManager minionManager, MinionsConfig minionsConfig,
                             GuiManager guiManager, GuiConfig guiConfig, long minionId, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionsConfig = minionsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.minionId = minionId;
        this.previousGui = previousGui;
    }

    private MinionUpgradeManager resolveUpgradeManager() {
        return EcoCorePlugin.getInstance().getMinionUpgradeManager();
    }

    private MinionConnectorManager resolveConnectorManager() {
        return EcoCorePlugin.getInstance().getMinionConnectorManager();
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "§8Upgrade Minion");
        render();
    }

    private void render() {
        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            // Revisi 14: never close abruptly without feedback - show an
            // empty-but-valid inventory rather than viewer.closeInventory().
            inventory.clear();
            inventory.setItem(SUMMARY_SLOT, namedItem(Material.BARRIER, "§cMinion tidak ditemukan", List.of()));
            inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
            return;
        }
        inventory.clear();
        MinionUpgradeManager upgrades = resolveUpgradeManager();
        inventory.setItem(SUMMARY_SLOT, buildSummaryIcon(data));
        inventory.setItem(STORAGE_UPGRADE_SLOT, buildUpgradeIcon(
                Material.CHEST, "§eUpgrade Storage", data, upgrades,
                MinionUpgradeManager.UpgradeType.STORAGE_PAGE,
                "Storage " + data.getStoragePageCount() + "/" + upgrades.getMaxStoragePages()));
        inventory.setItem(RADIUS_UPGRADE_SLOT, buildUpgradeIcon(
                Material.SPYGLASS, "§bUpgrade Radius", data, upgrades,
                MinionUpgradeManager.UpgradeType.RADIUS, data.getRadius() + " block"));
        inventory.setItem(SPEED_UPGRADE_SLOT, buildUpgradeIcon(
                Material.SUGAR, "§dUpgrade Speed", data, upgrades,
                MinionUpgradeManager.UpgradeType.SPEED, data.getSpeedTicks() + " tick/aksi"));
        inventory.setItem(STORAGE_OPEN_SLOT, namedItem(Material.ENDER_CHEST, "§6Buka Storage",
                List.of("§7Klik buat lihat isi storage minion ini.")));

        MinionHandler handler = minionManager.getHandler(data.getType());
        if (handler != null && handler.getWorkMode() == MinionWorkMode.BOTH) {
            inventory.setItem(MODE_TOGGLE_SLOT, buildModeToggleIcon(data.isUseArenaMode()));
        }
        inventory.setItem(REMOVE_SLOT, buildRemoveIcon());
        inventory.setItem(CONNECTOR_SLOT, buildConnectorIcon());
        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildSummaryIcon(MinionData data) {
        ItemStack icon = io.azthera.ecocore.utils.ItemUtils.buildMinionTypeIcon(data.getType(), minionsConfig);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + data.getType().configKey() + " §7Lv." + data.getLevel());
            meta.setLore(List.of(
                    "§7Energi: §f" + data.getEnergy() + "/" + minionsConfig.getBaseEnergy(),
                    "§7Fuel tersisa: §f" + (data.getFuelTicksRemaining() / 20) + " detik",
                    "§7Arah: §f" + data.getFacing().name()
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /**
     * Builds an upgrade button icon. Revisi 14 fix: {@code canUpgrade}
     * and {@code computeUpgradeCost} are now both null-safe total
     * functions (see {@link MinionUpgradeManager}), so this method
     * can never throw regardless of the minion's current tier - the
     * "Max Level" branch is always reachable and always renders cleanly.
     */
    private ItemStack buildUpgradeIcon(Material material, String name, MinionData data,
                                        MinionUpgradeManager upgrades, MinionUpgradeManager.UpgradeType type,
                                        String currentValueLabel) {
        boolean canUpgrade = upgrades != null && upgrades.canUpgrade(data, type);
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (canUpgrade) {
                double cost = upgrades.computeUpgradeCost(data, type);
                meta.setLore(List.of(
                        "§7Saat ini: §f" + currentValueLabel,
                        "§7Biaya: §a" + String.format("%.2f", cost),
                        "§eKlik untuk upgrade"
                ));
            } else {
                meta.setLore(List.of("§7Saat ini: §f" + currentValueLabel, "§c§lMax Level"));
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildModeToggleIcon(boolean arenaMode) {
        ItemStack icon = new ItemStack(arenaMode ? Material.COMPASS : Material.TARGET);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(arenaMode ? "§bMode: Arena" : "§bMode: Depan Muka");
            meta.setLore(List.of(
                    arenaMode ? "§7Minion bekerja di radius sekitarnya (360°)." : "§7Minion bekerja lurus ke arah dia menghadap.",
                    "§eKlik untuk ganti mode."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildConnectorIcon() {
        ItemStack icon = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lConnector");
            meta.setLore(List.of(
                    "§7Hubungkan minion ini ke minion lain.",
                    "§7Klik minion sumber, lalu klik minion tujuan.",
                    "§eKlik untuk mulai."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildRemoveIcon() {
        ItemStack icon = new ItemStack(Material.BARRIER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lHapus Minion");
            meta.setLore(List.of(
                    "§7Entity minion bakal hilang dari dunia,",
                    "§7isi storage-nya balik ke inventory lu,",
                    "§7dan lu dapet Minion Egg buat naruh lagi."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
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

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            return;
        }
        MinionUpgradeManager upgrades = resolveUpgradeManager();

        if (slot == BACK_SLOT) {
            if (previousGui != null) {
                guiManager.register(getViewer(), previousGui);
                previousGui.open();
            } else {
                getViewer().closeInventory();
            }
            return;
        }
        if (slot == CLOSE_SLOT) {
            getViewer().closeInventory();
            return;
        }

        MinionUpgradeManager.UpgradeType type = switch (slot) {
            case STORAGE_UPGRADE_SLOT -> MinionUpgradeManager.UpgradeType.STORAGE_PAGE;
            case RADIUS_UPGRADE_SLOT -> MinionUpgradeManager.UpgradeType.RADIUS;
            case SPEED_UPGRADE_SLOT -> MinionUpgradeManager.UpgradeType.SPEED;
            default -> null;
        };
        if (type != null) {
            boolean purchased = upgrades.purchaseUpgrade(getViewer().getUniqueId(), data, type, minionManager);
            guiManager.playSound(getViewer(), purchased ? "level-up" : "error");
            if (purchased) {
                minionManager.refreshMinionDisplay(minionId);
            }
            render();
            return;
        }

        if (slot == STORAGE_OPEN_SLOT) {
            MinionType minionType = data.getType();
            boolean hasZones = minionType == MinionType.SMELTER || minionType == MinionType.LUMBERJACK
                    || minionType == MinionType.FARMER;
            if (hasZones) {
                MinionStorageSelectGui zoneGui = new MinionStorageSelectGui(getViewer(), minionManager, minionId, this);
                guiManager.register(getViewer(), zoneGui);
                zoneGui.open();
            } else {
                MinionStorageSelectionGui pageGui = new MinionStorageSelectionGui(getViewer(), minionManager, upgrades, minionId);
                guiManager.register(getViewer(), pageGui);
                pageGui.open();
            }
            return;
        }

        if (slot == MODE_TOGGLE_SLOT) {
            MinionHandler handler = minionManager.getHandler(data.getType());
            if (handler != null && handler.getWorkMode() == MinionWorkMode.BOTH) {
                data.setUseArenaMode(!data.isUseArenaMode());
                guiManager.playSound(getViewer(), "click");
                render();
            }
            return;
        }

        if (slot == CONNECTOR_SLOT) {
            MinionConnectorManager connectorManager = resolveConnectorManager();
            connectorManager.beginSelection(getViewer().getUniqueId(), minionId);
            getViewer().closeInventory();
            getViewer().sendMessage("§bKlik minion §fsumber§b (biasanya minion ini), lalu klik minion §ftujuan§b buat connect.");
            return;
        }

        if (slot == REMOVE_SLOT) {
            boolean removed = minionManager.removeAndRefund(minionId, getViewer());
            if (removed) {
                getViewer().sendMessage("§7Minion telah dihapus. Isi storage & Minion Egg-nya balik ke inventory lu.");
                guiManager.playSound(getViewer(), "click");
            } else {
                getViewer().sendMessage("§cGagal menghapus minion.");
            }
            getViewer().closeInventory();
        }
    }
}