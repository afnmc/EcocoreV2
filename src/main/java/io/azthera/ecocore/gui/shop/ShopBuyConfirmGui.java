package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Confirmation screen shown before executing a purchase. Every
 * purchase from {@link ShopItemPreviewGui} routes through here so
 * confirmation, messaging, and sound feedback live in one place.
 */
public final class ShopBuyConfirmGui extends AbstractGui {

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final ShopManager shopManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;
    private final String itemId;
    private final int amount;
    private final double totalPrice;
    private final AbstractGui previousGui;

    /**
     * Creates the confirmation screen.
     *
     * @param viewer         the viewing player
     * @param shopManager    shared shop manager
     * @param guiManager     shared GUI manager
     * @param messagesConfig resolved messages.yml configuration
     * @param itemId         the item id being purchased
     * @param amount         the requested purchase quantity
     * @param totalPrice     the total price to display and charge on confirm
     * @param previousGui    the screen to return to on cancel
     */
    public ShopBuyConfirmGui(Player viewer, ShopManager shopManager, GuiManager guiManager,
                              MessagesConfig messagesConfig, String itemId, int amount,
                              double totalPrice, AbstractGui previousGui) {
        super(viewer);
        this.shopManager = shopManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
        this.itemId = itemId;
        this.amount = amount;
        this.totalPrice = totalPrice;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 27, "§8Konfirmasi Pembelian");

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§eBeli " + amount + "x " + itemId + "?");
            infoMeta.setLore(List.of("§7Total: §a" + String.format("%.2f", totalPrice)));
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
            ShopManager.BuyResult result = shopManager.buy(viewer.getUniqueId(), itemId, amount);

            if (result.success()) {
                viewer.sendMessage(messagesConfig.getWithPrefix("shop.bought",
                        "amount", String.valueOf(result.amount()),
                        "item", itemId,
                        "price", String.format("%.2f", result.totalPrice())));
                guiManager.playSound(viewer, "buy");
            } else if ("sold-out".equals(result.message())) {
                viewer.sendMessage(messagesConfig.getWithPrefix("shop.sold-out"));
                guiManager.playSound(viewer, "error");
            } else {
                viewer.sendMessage(messagesConfig.getWithPrefix("economy.not-enough-money"));
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