package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionConnectorManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * A single minion's Connector screen: shows every outgoing connection
 * currently drawn FROM this minion (click one to remove it), and
 * gives the player a Connector Tool to draw a new one. Reached from
 * the "Connector" button in {@link MinionUpgradeGui}.
 */
public final class ConnectorGui extends AbstractGui {

    private static final int GIVE_TOOL_SLOT = 4;
    private static final int CONNECTIONS_START_SLOT = 9;
    private static final int CONNECTIONS_END_SLOT = 44;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private final MinionManager minionManager;
    private final MinionConnectorManager connectorManager;
    private final GuiManager guiManager;
    private final long minionId;
    private final AbstractGui previousGui;

    /**
     * Creates the connector screen for a minion.
     *
     * @param viewer           the viewing player
     * @param minionManager    shared minion manager
     * @param connectorManager shared Connector Network manager
     * @param guiManager       shared GUI manager
     * @param minionId         this screen's minion's database id
     * @param previousGui      the screen to return to
     */
    public ConnectorGui(Player viewer, MinionManager minionManager, MinionConnectorManager connectorManager,
                         GuiManager guiManager, long minionId, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.connectorManager = connectorManager;
        this.guiManager = guiManager;
        this.minionId = minionId;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "§8Connector - Koneksi Aktif");
        render();
    }

    private void render() {
        for (int slot = CONNECTIONS_START_SLOT; slot <= CONNECTIONS_END_SLOT; slot++) {
            inventory.setItem(slot, null);
        }

        ItemStack giveTool = ItemUtils.buildConnectorTool();
        ItemMeta toolMeta = giveTool.getItemMeta();
        if (toolMeta != null) {
            toolMeta.setLore(List.of(
                    "§7Klik buat dapetin Connector Tool ke inventory lu.",
                    "§7Klik kanan minion ini dulu (source), lalu klik",
                    "§7kanan minion lain (destination) buat bikin koneksi baru."
            ));
            giveTool.setItemMeta(toolMeta);
        }
        inventory.setItem(GIVE_TOOL_SLOT, giveTool);

        List<Long> destinationIds = connectorManager.listConnectionsFrom(minionId);
        int slot = CONNECTIONS_START_SLOT;
        for (Long destinationId : destinationIds) {
            if (slot > CONNECTIONS_END_SLOT) {
                break;
            }
            MinionData destinationData = minionManager.getMinion(destinationId);
            inventory.setItem(slot, buildConnectionIcon(destinationId, destinationData));
            slot++;
        }

        if (destinationIds.isEmpty()) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta emptyMeta = empty.getItemMeta();
            if (emptyMeta != null) {
                emptyMeta.setDisplayName("§7Belum ada koneksi keluar dari minion ini.");
                empty.setItemMeta(emptyMeta);
            }
            inventory.setItem(CONNECTIONS_START_SLOT, empty);
        }

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildConnectionIcon(long destinationId, MinionData destinationData) {
        ItemStack icon = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            String label = destinationData != null
                    ? destinationData.getType().configKey() + " §7(#" + destinationId + ")"
                    : "§8[minion terhapus] §7(#" + destinationId + ")";
            meta.setDisplayName("§a➜ §f" + label);
            meta.setLore(List.of("§7Klik buat hapus koneksi ini."));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == GIVE_TOOL_SLOT) {
            var leftover = viewer.getInventory().addItem(ItemUtils.buildConnectorTool());
            for (ItemStack over : leftover.values()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), over);
            }
            viewer.sendMessage("§bConnector Tool ditambahkan ke inventory lu.");
            guiManager.playSound(viewer, "click");
            return;
        }

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

        if (slot >= CONNECTIONS_START_SLOT && slot <= CONNECTIONS_END_SLOT) {
            List<Long> destinationIds = connectorManager.listConnectionsFrom(minionId);
            int index = slot - CONNECTIONS_START_SLOT;
            if (index < destinationIds.size()) {
                long destinationId = destinationIds.get(index);
                connectorManager.disconnect(minionId, destinationId);
                viewer.sendMessage("§7Koneksi ke #" + destinationId + " dihapus.");
                guiManager.playSound(viewer, "click");
                render();
            }
        }
    }
}
