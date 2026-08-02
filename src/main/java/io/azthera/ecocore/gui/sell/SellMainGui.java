package io.azthera.ecocore.gui.sell;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.sell.AutoSellManager;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The root {@code /sell} screen: quick actions for Sell Inventory,
 * Sell Chest, and toggling auto-sell, plus a drag-and-drop slot for
 * selling a single item stack.
 */
public final class SellMainGui extends AbstractGui {

    private static final int DROP_SLOT = 13;
    private static final int SELL_DROPPED_SLOT = 22;
    private static final int SELL_INVENTORY_SLOT = 29;
    private static final int SELL_CHEST_SLOT = 31;
    private static final int AUTO_SELL_TOGGLE_SLOT = 33;
    private static final int CLOSE_SLOT = 40;

    private final SellManager sellManager;
    private final AutoSellManager autoSellManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;

    /**
     * Creates the sell main screen.
     *
     * @param viewer          the viewing player
     * @param sellManager     shared sell manager
     * @param autoSellManager shared auto-sell manager
     * @param guiManager      shared GUI manager
     * @param messagesConfig  resolved messages.yml configuration
     */
    public SellMainGui(Player viewer, SellManager sellManager, AutoSellManager autoSellManager,
                        GuiManager guiManager, MessagesConfig messagesConfig) {
        super(viewer);
        this.sellManager = sellManager;
        this.autoSellManager = autoSellManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 45, "§8Sell");
        render();
    }

    private void render() {
        inventory.setItem(SELL_DROPPED_SLOT, buildActionIcon(Material.HOPPER, "§aJual Barang Ini",
                "§7Taruh barang di slot tengah,", "§7lalu klik ikon ini."));
        inventory.setItem(SELL_INVENTORY_SLOT, buildActionIcon(Material.CHEST, "§6Sell All (Inventory)",
                "§7Jual semua barang yang bisa", "§7dijual di inventory kamu."));
        inventory.setItem(SELL_CHEST_SLOT, buildActionIcon(Material.ENDER_CHEST, "§dSell Chest",
                "§7Buka chest, lalu gunakan", "§7/sell chest saat chest terbuka."));

        boolean autoSellOn = autoSellManager.isEnabled(viewer.getUniqueId());
        inventory.setItem(AUTO_SELL_TOGGLE_SLOT, buildActionIcon(
                autoSellOn ? Material.LIME_DYE : Material.GRAY_DYE,
                autoSellOn ? "§a§lAuto Sell: ON" : "§7§lAuto Sell: OFF",
                "§7Klik untuk toggle auto-sell."));

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildActionIcon(Material material, String name, String... lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == DROP_SLOT) {
            // Allow the player to freely place/remove an item stack here.
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);

        if (slot == SELL_DROPPED_SLOT) {
            ItemStack toSell = inventory.getItem(DROP_SLOT);
            if (toSell == null || toSell.getType().isAir()) {
                viewer.sendMessage(messagesConfig.getWithPrefix("sell.nothing-to-sell"));
                return;
            }

            SellManager.SellResult result = sellManager.sellSingle(viewer.getUniqueId(), toSell);
            if (result.success()) {
                inventory.setItem(DROP_SLOT, null);
                viewer.sendMessage(messagesConfig.getWithPrefix("sell.sold",
                        "amount", String.valueOf(result.totalAmount()),
                        "item", toSell.getType().name(),
                        "price", String.format("%.2f", result.totalPayout())));
                guiManager.playSound(viewer, "sell");
            } else {
                viewer.sendMessage(messagesConfig.getWithPrefix("sell.blacklisted"));
                guiManager.playSound(viewer, "error");
            }
            return;
        }

        if (slot == SELL_INVENTORY_SLOT) {
            SellConfirmGui confirmGui = new SellConfirmGui(
                    viewer, sellManager, guiManager, messagesConfig, SellConfirmGui.Mode.INVENTORY, null, this);
            guiManager.register(viewer, confirmGui);
            confirmGui.open();
            return;
        }

        if (slot == SELL_CHEST_SLOT) {
            viewer.sendMessage("§7Buka chest yang mau dijual, lalu ketik §f/sell chest§7.");
            viewer.closeInventory();
            return;
        }

        if (slot == AUTO_SELL_TOGGLE_SLOT) {
            boolean nowEnabled = autoSellManager.toggle(viewer.getUniqueId());
            viewer.sendMessage(nowEnabled ? "§aAuto-sell diaktifkan." : "§7Auto-sell dimatikan.");
            render();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
        }
    }
}