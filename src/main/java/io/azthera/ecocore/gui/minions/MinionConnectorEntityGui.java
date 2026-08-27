package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionConnectorEntityManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class MinionConnectorEntityGui extends AbstractGui {

    private static final int SUMMARY_SLOT = 4;
    private static final int UPGRADE_SLOT = 22;
    private static final int CLOSE_SLOT = 31;

    private final MinionConnectorEntityManager connectorEntityManager;
    private final GuiManager guiManager;
    private final long connectorId;

    public MinionConnectorEntityGui(Player viewer, MinionConnectorEntityManager connectorEntityManager,
                                     GuiManager guiManager, long connectorId) {
        super(viewer);
        this.connectorEntityManager = connectorEntityManager;
        this.guiManager = guiManager;
        this.connectorId = connectorId;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 36, "\u00a78Minion Connector");
        render();
    }

    private void render() {
        inventory.clear();
        MinionConnectorEntityManager.ActiveConnector connector = connectorEntityManager.getConnector(connectorId);
        if (connector == null) {
            inventory.setItem(SUMMARY_SLOT, namedItem(Material.BARRIER, "\u00a7cConnector tidak ditemukan", List.of()));
            inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "\u00a7cTutup"));
            return;
        }
        double maxDistance = connectorEntityManager.getMaxRelayDistance(connector);
        inventory.setItem(SUMMARY_SLOT, namedItem(Material.BLAZE_ROD, "\u00a7b\u00a7lMinion Connector",
                List.of("\u00a77Range saat ini: \u00a7f" + (int) maxDistance + " block",
                        "\u00a77Level upgrade: \u00a7f" + connector.getRangeLevel())));
        boolean canUpgrade = connectorEntityManager.canUpgradeRange(connector);
        ItemStack upgradeIcon = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = upgradeIcon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7eUpgrade Range");
            if (canUpgrade) {
                double cost = connectorEntityManager.computeUpgradeCost(connector);
                meta.setLore(List.of(
                        "\u00a77Range saat ini: \u00a7f" + (int) maxDistance + " block",
                        "\u00a77Biaya: \u00a7a" + String.format("%.2f", cost),
                        "\u00a7eKlik untuk upgrade"
                ));
            } else {
                meta.setLore(List.of("\u00a77Range saat ini: \u00a7f" + (int) maxDistance + " block", "\u00a7c\u00a7lMax Level"));
            }
            upgradeIcon.setItemMeta(meta);
        }
        inventory.setItem(UPGRADE_SLOT, upgradeIcon);
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "\u00a7cTutup"));
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
        if (slot == CLOSE_SLOT) {
            getViewer().closeInventory();
            return;
        }
        if (slot == UPGRADE_SLOT) {
            MinionConnectorEntityManager.ActiveConnector connector = connectorEntityManager.getConnector(connectorId);
            if (connector == null || !connectorEntityManager.canUpgradeRange(connector)) {
                guiManager.playSound(getViewer(), "error");
                return;
            }
            double cost = connectorEntityManager.computeUpgradeCost(connector);
            io.azthera.ecocore.economy.EconomyEngine economyEngine = io.azthera.ecocore.EcoCorePlugin.getInstance().getEconomyEngine();
            if (!economyEngine.has(getViewer().getUniqueId(), cost)) {
                getViewer().sendMessage("\u00a7cUang lu gak cukup.");
                guiManager.playSound(getViewer(), "error");
                return;
            }
            if (!economyEngine.withdraw(getViewer().getUniqueId(), cost, io.azthera.ecocore.economy.TransactionLogger.REASON_ADMIN_ADJUST)) {
                guiManager.playSound(getViewer(), "error");
                return;
            }
            connectorEntityManager.applyRangeUpgrade(connector);
            guiManager.playSound(getViewer(), "level-up");
            render();
        }
    }
}
