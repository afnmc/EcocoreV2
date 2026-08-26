// FILE: src/main/java/io/azthera/ecocore/gui/minions/MinionConnectorEntityGui.java
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

/**
 * Range upgrade screen for a {@code MinionConnectorEntity} (Revisi
 * 9). Same max-level-safety convention as {@link MinionUpgradeGui}.
 */
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
        inventory = Bukkit.createInventory(this, 36, "§8Minion Connector");
        render();
    }

    private void render() {
        inventory.clear();
        MinionConnectorEntityManager.ActiveConnector connector = connectorEntityManager.getConnector(connectorId);
        if (connector == null) {
            inventory.setItem(SUMMARY_SLOT, namedItem(Material.BARRIER, "§cConnector tidak ditemukan", List.of()));
            inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
            return;
        }
        double maxDistance = connectorEntityManager.getMaxRelayDistance(connector);
        inventory.setItem(SUMMARY_SLOT, namedItem(Material.BLAZE_ROD, "§b§lMinion Connector",
                List.of("§7Range saat ini: §f" + (int) maxDistance + " block",
                        "§7Level upgrade: §f" + connector.getRangeLevel())));
        boolean canUpgrade = connectorEntityManager.canUpgradeRange(connector);
        ItemStack upgradeIcon = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = upgradeIcon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eUpgrade Range");
            if (canUpgrade) {
                double cost = connectorEntityManager.computeUpgradeCost(connector);
                meta.setLore(List.of(
                        "§7Range saat ini: §f" + (int) maxDistance + " block",
                        "§7Biaya: §a" + String.format("%.2f", cost),
                        "§eKlik untuk upgrade"
                ));
            } else {
                meta.setLore(List.of("§7Range saat ini: §f" + (int) maxDistance + " block", "§c§lMax Level"));
            }
            upgradeIcon.setItemMeta(meta);
        }
        inventory.setItem(UPGRADE_SLOT, upgradeIcon);
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
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
                getViewer().sendMessage("§cUang lu gak cukup.");
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