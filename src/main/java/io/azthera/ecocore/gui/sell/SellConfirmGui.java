package io.azthera.ecocore.gui.sell;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Confirmation screen shown before a bulk sell (Sell Inventory or
 * Sell Chest), previewing the total profit before committing.
 */
public final class SellConfirmGui extends AbstractGui {

    /**
     * Which bulk-sell operation this confirmation is for.
     */
    public enum Mode {
        INVENTORY,
        CHEST
    }

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final SellManager sellManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;
    private final Mode mode;
    private final Inventory targetInventory;
    private final AbstractGui previousGui;

    /**
     * Creates the bulk-sell confirmation screen.
     *
     * @param viewer          the viewing player
     * @param sellManager     shared sell manager
     * @param guiManager      shared GUI manager
     * @param messagesConfig  resolved messages.yml configuration
     * @param mode            whether this confirms an inventory sale or a chest sale
     * @param targetInventory the container inventory to sell, only used when {@code mode} is {@link Mode#CHEST}
     * @param previousGui     the screen to return to on cancel
     */
    public SellConfirmGui(Player viewer, SellManager sellManager, GuiManager guiManager, MessagesConfig messagesConfig,
                           Mode mode, Inventory targetInventory, AbstractGui previousGui) {
        super(viewer);
        this.sellManager = sellManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
        this.mode = mode;
        this.targetInventory = targetInventory;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 27, "§8Konfirmasi Jual");

        String targetLabel = mode == Mode.INVENTORY ? "seluruh inventory" : "chest ini";

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§eJual semua barang di " + targetLabel + "?");
            infoMeta.setLore(List.of("§7Barang yang tidak bisa dijual akan dilewati."));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(13, info);

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName("§a§lKonfirmasi");
            confirm.setItemMeta(confirmMeta);
        }
        inventory.setItem(CONFIRM_SLOT, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName("§c§lBatal");
            cancel.setItemMeta(cancelMeta);
        }
        inventory.setItem(CANCEL_SLOT, cancel);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == CONFIRM_SLOT) {
            Inventory target = mode == Mode.INVENTORY ? viewer.getInventory() : targetInventory;
            SellManager.SellResult result = mode == Mode.INVENTORY
                    ? sellManager.sellAll(viewer.getUniqueId(), target)
                    : sellManager.sellChest(viewer.getUniqueId(), target);

            if (result.success()) {
                viewer.sendMessage(messagesConfig.getWithPrefix("sell.sold",
                        "amount", String.valueOf(result.totalAmount()),
                        "item", "barang",
                        "price", String.format("%.2f", result.totalPayout())));
                guiManager.playSound(viewer, "sell");
            } else {
                viewer.sendMessage(messagesConfig.getWithPrefix("sell.nothing-to-sell"));
                guiManager.playSound(viewer, "error");
            }

            viewer.closeInventory();
            return;
        }

        if (slot == CANCEL_SLOT) {
            if (previousGui != null) {
                guiManager.register(viewer, previousGui);
                previousGui.open();
            } else {
                viewer.closeInventory();
            }
        }
    }
}