package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.minions.MinionUpgradeManager;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.MinionProcessingType;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Detail/upgrade screen for a single minion: shows its current
 * stats and lets the owner purchase storage, radius, and speed
 * upgrades, toggle auto-repair/auto-sell/auto-smelt, and remove it
 * (which returns a fresh Minion Egg so it can be placed again elsewhere).
 */
public final class MinionUpgradeGui extends AbstractGui {

    private static final int SUMMARY_SLOT = 4;
    private static final int STORAGE_UPGRADE_SLOT = 20;
    private static final int RADIUS_UPGRADE_SLOT = 22;
    private static final int SPEED_UPGRADE_SLOT = 24;
    private static final int AUTO_SELL_TOGGLE_SLOT = 29;
    private static final int AUTO_SMELT_TOGGLE_SLOT = 31;
    private static final int MODE_TOGGLE_SLOT = 38;
    private static final int CONNECTOR_SLOT = 42;
    private static final int REMOVE_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private final MinionManager minionManager;
    private final MinionsConfig minionsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final long minionId;
    private final AbstractGui previousGui;

    /**
     * Creates the minion upgrade screen.
     *
     * @param viewer        the viewing player
     * @param minionManager shared minion manager
     * @param minionsConfig resolved minions.yml configuration
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     * @param minionId      the minion's database id
     * @param previousGui   the screen to return to
     */
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
        return io.azthera.ecocore.EcoCorePlugin.getInstance().getMinionUpgradeManager();
    }

    private io.azthera.ecocore.minions.MinionConnectorManager resolveConnectorManager() {
        return io.azthera.ecocore.EcoCorePlugin.getInstance().getMinionConnectorManager();
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "§8Upgrade Minion");
        render();
    }

    private void render() {
        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            viewer.closeInventory();
            return;
        }

        MinionUpgradeManager upgrades = resolveUpgradeManager();

        inventory.setItem(SUMMARY_SLOT, buildSummaryIcon(data));

        inventory.setItem(STORAGE_UPGRADE_SLOT, buildUpgradeIcon(
                Material.CHEST, "§eUpgrade Storage", data, upgrades, MinionUpgradeManager.UpgradeType.STORAGE,
                data.getStorageSlots() + " slot"));

        inventory.setItem(RADIUS_UPGRADE_SLOT, buildUpgradeIcon(
                Material.SPYGLASS, "§bUpgrade Radius", data, upgrades, MinionUpgradeManager.UpgradeType.RADIUS,
                data.getRadius() + " block"));

        inventory.setItem(SPEED_UPGRADE_SLOT, buildUpgradeIcon(
                Material.SUGAR, "§dUpgrade Speed", data, upgrades, MinionUpgradeManager.UpgradeType.SPEED,
                data.getSpeedTicks() + " tick/aksi"));

        inventory.setItem(AUTO_SMELT_TOGGLE_SLOT, buildToggleIcon("Auto Smelt", data.isAutoSmelt()));

        // Auto Sell only makes sense for the Sell Minion itself - every
        // other minion type just fills its own storage and relies on a
        // Collector/Sell Minion downstream, so the toggle is hidden for
        // them entirely instead of showing a switch that does nothing.
        if (data.getType() == io.azthera.ecocore.model.MinionType.SELLER) {
            inventory.setItem(AUTO_SELL_TOGGLE_SLOT, buildToggleIcon("Auto Sell", data.isAutoSell()));
        }

        MinionHandler handler = minionManager.getHandler(data.getType());
        if (handler != null && handler.getProcessingType() == MinionProcessingType.BLOCK_BREAK) {
            boolean facingMode = minionManager.isFacingModeEnabled(minionId);
            inventory.setItem(MODE_TOGGLE_SLOT, buildModeToggleIcon(facingMode));
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
                    "§7Fuel tersisa: §f" + (data.getFuelTicksRemaining() / 20) + " detik"
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

    private ItemStack buildToggleIcon(String label, boolean enabled) {
        ItemStack icon = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((enabled ? "§a" : "§7") + label + ": " + (enabled ? "ON" : "OFF"));
            meta.setLore(List.of("§7Klik untuk toggle."));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /**
     * Builds the mining-mode toggle icon, only shown for minions
     * whose handler processing type is {@code BLOCK_BREAK} (Miner,
     * Quarry, Lumberjack). "Arena mode" is the original behavior
     * (nearest matching block anywhere in radius); "facing mode"
     * mines in a straight line in the direction the minion's entity
     * is facing, like a tunnel borer.
     *
     * @param facingMode whether facing mode is currently active
     * @return the built toggle icon
     */
    private ItemStack buildModeToggleIcon(boolean facingMode) {
        ItemStack icon = new ItemStack(facingMode ? Material.TARGET : Material.COMPASS);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(facingMode ? "§bMode: Depan Muka" : "§bMode: Arena");
            meta.setLore(List.of(
                    facingMode
                            ? "§7Minion nambang lurus ke arah dia menghadap,"
                            : "§7Minion nambang blok terdekat di area radius,",
                    facingMode
                            ? "§7kayak nge-bor terowongan lurus ke depan."
                            : "§7gak peduli arah hadap minion-nya.",
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
                    "§7Lihat & atur kemana minion ini ngirim barang",
                    "§7lewat Connector Network.",
                    "§eKlik untuk buka."
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

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            viewer.closeInventory();
            return;
        }

        MinionUpgradeManager upgrades = resolveUpgradeManager();

        if (slot == BACK_SLOT) {
            if (previousGui != null) {
                guiManager.register(viewer, previousGui);
                previousGui.open();
            } else {
                viewer.closeInventory();
            }
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        if (upgrades != null) {
            MinionUpgradeManager.UpgradeType type = switch (slot) {
                case STORAGE_UPGRADE_SLOT -> MinionUpgradeManager.UpgradeType.STORAGE;
                case RADIUS_UPGRADE_SLOT -> MinionUpgradeManager.UpgradeType.RADIUS;
                case SPEED_UPGRADE_SLOT -> MinionUpgradeManager.UpgradeType.SPEED;
                default -> null;
            };
            if (type != null) {
                boolean purchased = upgrades.purchaseUpgrade(viewer.getUniqueId(), data, type);
                if (purchased) {
                    guiManager.playSound(viewer, "level-up");
                    minionManager.refreshMinionDisplay(minionId);
                } else {
                    guiManager.playSound(viewer, "error");
                }
                render();
                return;
            }
        }

        if (slot == AUTO_SELL_TOGGLE_SLOT && data.getType() == io.azthera.ecocore.model.MinionType.SELLER) {
            data.setAutoSell(!data.isAutoSell());
            render();
            return;
        }
        if (slot == AUTO_SMELT_TOGGLE_SLOT) {
            data.setAutoSmelt(!data.isAutoSmelt());
            render();
            return;
        }

        if (slot == MODE_TOGGLE_SLOT) {
            MinionHandler handler = minionManager.getHandler(data.getType());
            if (handler != null && handler.getProcessingType() == MinionProcessingType.BLOCK_BREAK) {
                boolean nowFacing = !minionManager.isFacingModeEnabled(minionId);
                minionManager.setFacingModeEnabled(minionId, nowFacing);
                guiManager.playSound(viewer, "click");
                render();
            }
            return;
        }

        if (slot == CONNECTOR_SLOT) {
            ConnectorGui connectorGui = new ConnectorGui(
                    viewer, minionManager, resolveConnectorManager(), guiManager, minionId, this);
            guiManager.register(viewer, connectorGui);
            connectorGui.open();
            return;
        }

        if (slot == REMOVE_SLOT) {
            boolean removed = minionManager.removeAndRefund(minionId, viewer);
            if (removed) {
                viewer.sendMessage("§7Minion telah dihapus. Isi storage & Minion Egg-nya balik ke inventory lu.");
                guiManager.playSound(viewer, "click");
            } else {
                viewer.sendMessage("§cGagal menghapus minion.");
            }
            viewer.closeInventory();
        }
    }
                                                                 }
