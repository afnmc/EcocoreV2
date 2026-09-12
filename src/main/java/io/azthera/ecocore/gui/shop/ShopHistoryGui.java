package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.model.TransactionRecord;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.List;

/**
 * Shows a player's most recent buy/sell transactions, newest first.
 */
public final class ShopHistoryGui extends AbstractGui {

    private static final int CLOSE_SLOT = 49;
    private static final int MAX_ENTRIES = 45;

    private final ShopManager shopManager;
    private final GuiManager guiManager;

    /**
     * Creates the transaction history screen.
     *
     * @param viewer      the viewing player
     * @param shopManager shared shop manager
     * @param guiManager  shared GUI manager
     */
    public ShopHistoryGui(Player viewer, ShopManager shopManager, GuiManager guiManager) {
        super(viewer);
        this.shopManager = shopManager;
        this.guiManager = guiManager;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, io.azthera.ecocore.EcoCorePlugin.getInstance().getMessagesConfig().get("ui.shop.history-title"));

        try {
            List<TransactionRecord> history = shopManager.getHistory(viewer.getUniqueId(), MAX_ENTRIES);
            int slot = 0;
            for (TransactionRecord record : history) {
                inventory.setItem(slot++, buildHistoryIcon(record));
            }
        } catch (SQLException exception) {
            viewer.sendMessage(io.azthera.ecocore.EcoCorePlugin.getInstance().getMessagesConfig().getWithPrefix("ui.jobs.load-failed"));
        }

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildHistoryIcon(TransactionRecord record) {
        boolean isBuy = record.type() == TransactionRecord.TransactionType.BUY;
        ItemStack icon = new ItemStack(isBuy ? Material.LIME_DYE : Material.RED_DYE, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(io.azthera.ecocore.EcoCorePlugin.getInstance().getMessagesConfig().get(isBuy ? "ui.shop.history-buy" : "ui.shop.history-sell", "amount", String.valueOf(record.amount()), "item", record.itemId()));
            meta.setLore(List.of(
                    "§7Harga satuan: §f" + String.format("%.2f", record.unitPrice()),
                    "§7Total: §f" + String.format("%.2f", record.totalPrice())
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() == CLOSE_SLOT) {
            viewer.closeInventory();
        }
    }
}