package io.azthera.ecocore.gui.market;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.market.NightMarketManager;
import io.azthera.ecocore.model.NightMarketOffer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Confirmation screen for a single night market purchase (always 1x
 * per confirm, since these are rare/limited items).
 */
public final class NightMarketBuyConfirmGui extends AbstractGui {

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final NightMarketManager nightMarketManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;
    private final String offerId;
    private final AbstractGui previousGui;

    /**
     * Creates the confirmation screen.
     *
     * @param viewer             the viewing player
     * @param nightMarketManager shared night market manager
     * @param guiManager         shared GUI manager
     * @param messagesConfig     resolved messages.yml configuration
     * @param offerId            the offer id being purchased
     * @param previousGui        the screen to return to on cancel
     */
    public NightMarketBuyConfirmGui(Player viewer, NightMarketManager nightMarketManager, GuiManager guiManager,
                                     MessagesConfig messagesConfig, String offerId, AbstractGui previousGui) {
        super(viewer);
        this.nightMarketManager = nightMarketManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
        this.offerId = offerId;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 27, "§8Konfirmasi - Night Market");

        NightMarketOffer offer = nightMarketManager.getOffer(offerId);
        double price = offer != null ? offer.getPrice() : 0;

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§eBeli 1x " + offerId + "?");
            infoMeta.setLore(List.of("§7Harga: §a" + String.format("%.2f", price)));
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
            NightMarketManager.BuyResult result = nightMarketManager.buy(viewer, offerId, 1);

            if (result.success()) {
                viewer.sendMessage(messagesConfig.getWithPrefix("shop.bought",
                        "amount", String.valueOf(result.amount()),
                        "item", offerId,
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