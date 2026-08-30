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
        inventory = Bukkit.createInventory(this, 54, "\u00a78Upgrade Minion");
        render();
    }

    private void render() {
        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            inventory.clear();
            inventory.setItem(SUMMARY_SLOT, namedItem(Material.BARRIER, "\u00a7cMinion tidak ditemukan", List.of()));
            inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "\u00a7cTutup"));
            return;
        }
        inventory.clear();
        MinionUpgradeManager upgrades = resolveUpgradeManager();
        inventory.setItem(SUMMARY_SLOT, buildSummaryIcon(data));
        boolean isStorageType = data.getType() == MinionType.STORAGE;
        MinionUpgradeManager.UpgradeType storageUpgradeType = isStorageType
                ? MinionUpgradeManager.UpgradeType.STORAGE_PAGE
                : MinionUpgradeManager.UpgradeType.STORAGE_SLOTS;
        String storageUpgradeLabel = isStorageType
                ? "Storage " + data.getStoragePageCount() + "/" + upgrades.getMaxStoragePages() + " halaman"
                : "Slot " + data.getActiveSlotCount() + "/" + upgrades.getMaxActiveSlotCount();
        inventory.setItem(STORAGE_UPGRADE_SLOT, buildUpgradeIcon(
                Material.CHEST, "\u00a7eUpgrade Storage", data, upgrades,
                storageUpgradeType, storageUpgradeLabel));
        inventory.setItem(RADIUS_UPGRADE_SLOT, buildUpgradeIcon(
                Material.SPYGLASS, "\u00a7bUpgrade Radius", data, upgrades,
                MinionUpgradeManager.UpgradeType.RADIUS, data.getRadius() + " block"));
        inventory.setItem(SPEED_UPGRADE_SLOT, buildUpgradeIcon(
                Material.SUGAR, "\u00a7dUpgrade Speed", data, upgrades,
                MinionUpgradeManager.UpgradeType.SPEED, data.getSpeedTicks() + " tick/aksi"));
        inventory.setItem(STORAGE_OPEN_SLOT, namedItem(Material.ENDER_CHEST, "\u00a76Buka Storage",
                List.of("\u00a77Klik buat lihat isi storage minion ini.")));

        MinionHandler handler = minionManager.getHandler(data.getType());
        if (handler != null && handler.getWorkMode() == MinionWorkMode.BOTH) {
            inventory.setItem(MODE_TOGGLE_SLOT, buildModeToggleIcon(data.isUseArenaMode()));
        }
        inventory.setItem(REMOVE_SLOT, buildRemoveIcon());
        inventory.setItem(CONNECTOR_SLOT, buildConnectorIcon());
        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "\u00a7eKembali"));
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "\u00a7cTutup"));
    }

    private ItemStack buildSummaryIcon(MinionData data) {
        ItemStack icon = io.azthera.ecocore.utils.ItemUtils.buildMinionTypeIcon(data.getType(), minionsConfig);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7f" + data.getType().configKey() + " \u00a77Lv." + data.getLevel());
            meta.setLore(List.of(
                    "\u00a77Energi: \u00a7f" + data.getEnergy() + "/" + minionsConfig.getBaseEnergy(),
                    "\u00a77Fuel tersisa: \u00a7f" + (data.getFuelTicksRemaining() / 20) + " detik",
                    "\u00a77Arah: \u00a7f" + data.getFacing().name()
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

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
                        "\u00a77Saat ini: \u00a7f" + currentValueLabel,
                        "\u00a77Biaya: \u00a7a" + String.format("%.2f", cost),
                        "\u00a7eKlik untuk upgrade"
                ));
            } else {
                meta.setLore(List.of("\u00a77Saat ini: \u00a7f" + currentValueLabel, "\u00a7c\u00a7lMax Level"));
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildModeToggleIcon(boolean arenaMode) {
        ItemStack icon = new ItemStack(arenaMode ? Material.COMPASS : Material.TARGET);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(arenaMode ? "\u00a7bMode: Arena" : "\u00a7bMode: Depan Muka");
            meta.setLore(List.of(
                    arenaMode ? "\u00a77Minion bekerja di radius sekitarnya (360\u00b0)." : "\u00a77Minion bekerja lurus ke arah dia menghadap.",
                    "\u00a7eKlik untuk ganti mode."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildConnectorIcon() {
        ItemStack icon = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7b\u00a7lConnector");
            meta.setLore(List.of(
                    "\u00a77Hubungkan minion ini ke minion lain.",
                    "\u00a77Klik minion sumber, lalu klik minion tujuan.",
                    "\u00a7eKlik untuk mulai."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildRemoveIcon() {
        ItemStack icon = new ItemStack(Material.BARRIER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7c\u00a7lHapus Minion");
            meta.setLore(List.of(
                    "\u00a77Entity minion bakal hilang dari dunia,",
                    "\u00a77isi storage-nya balik ke inventory lu,",
                    "\u00a77dan lu dapet Minion Egg buat naruh lagi."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
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
            case STORAGE_UPGRADE_SLOT -> data.getType() == MinionType.STORAGE
                    ? MinionUpgradeManager.UpgradeType.STORAGE_PAGE
                    : MinionUpgradeManager.UpgradeType.STORAGE_SLOTS;
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
            if (minionType == MinionType.STORAGE) {
                MinionStorageSelectionGui pageGui = new MinionStorageSelectionGui(getViewer(), minionManager, upgrades, minionId);
                guiManager.register(getViewer(), pageGui);
                pageGui.open();
            } else if (hasZones) {
                MinionStorageSelectGui zoneGui = new MinionStorageSelectGui(getViewer(), minionManager, minionId, this);
                guiManager.register(getViewer(), zoneGui);
                zoneGui.open();
            } else {
                MinionSingleStorageGui singleGui = new MinionSingleStorageGui(getViewer(), minionManager, minionId);
                guiManager.register(getViewer(), singleGui);
                singleGui.open();
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
            MinionConnectListGui listGui = new MinionConnectListGui(
                    getViewer(), minionManager, connectorManager, guiManager, minionId, this);
            guiManager.register(getViewer(), listGui);
            listGui.open();
            return;
        }

        if (slot == REMOVE_SLOT) {
            boolean removed = minionManager.removeAndRefund(minionId, getViewer());
            if (removed) {
                getViewer().sendMessage("\u00a77Minion telah dihapus. Isi storage & Minion Egg-nya balik ke inventory lu.");
                guiManager.playSound(getViewer(), "click");
            } else {
                getViewer().sendMessage("\u00a7cGagal menghapus minion.");
            }
            getViewer().closeInventory();
        }
    }
}
