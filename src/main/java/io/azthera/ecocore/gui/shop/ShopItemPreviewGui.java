// FILE: src/main/java/io/azthera/ecocore/gui/shop/ShopItemPreviewGui.java
package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.inflation.PriceDisplayHelper;
import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed preview of a single shop item: price, stock, and quick
 * buy buttons for common quantities plus Buy Max. Purchases above
 * {@code shop.yml buying.require-confirmation-above} route through
 * {@link ShopBuyConfirmGui}; smaller purchases still do, for a
 * consistent single code path and to keep confirmation logic in one
 * place.
 *
 * Revisi 16: the price lore now also shows the inflation/deflation
 * explanation line whenever the server's economic state is currently
 * moving prices away from the item's base price.
 */
public final class ShopItemPreviewGui extends AbstractGui {

    private static final int ICON_SLOT = 13;
    private static final int BUY_1_SLOT = 19;
    private static final int BUY_10_SLOT = 20;
    private static final int BUY_64_SLOT = 21;
    private static final int BUY_MAX_SLOT = 22;
    private static final int FAVORITE_SLOT = 23;
    private static final int BACK_SLOT = 27;
    private static final int CLOSE_SLOT = 31;

    private final ShopManager shopManager;
    private final ShopConfig shopConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;
    private final String itemId;

    public ShopItemPreviewGui(Player viewer, ShopManager shopManager, ShopConfig shopConfig, GuiManager guiManager,
                               GuiConfig guiConfig, MessagesConfig messagesConfig, String itemId) {
        super(viewer);
        this.shopManager = shopManager;
        this.shopConfig = shopConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
        this.itemId = itemId;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 36, "§8Preview Item");
        render();
    }

    private void render() {
        ShopItemRecord item = shopManager.getItem(itemId);
        if (item == null) {
            getViewer().closeInventory();
            return;
        }
        inventory.setItem(ICON_SLOT, buildPreviewIcon(item));
        inventory.setItem(BUY_1_SLOT, buildBuyButton(item, 1));
        inventory.setItem(BUY_10_SLOT, buildBuyButton(item, 10));
        inventory.setItem(BUY_64_SLOT, buildBuyButton(item, 64));
        inventory.setItem(BUY_MAX_SLOT, buildBuyMaxButton(item));
        boolean favorite = shopManager.isFavorite(getViewer().getUniqueId(), itemId);
        inventory.setItem(FAVORITE_SLOT, guiManager.buildButtonIcon(
                favorite ? "favorite-on" : "favorite-off",
                favorite ? "§dHapus dari Favorit" : "§7Tambah ke Favorit"));
        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildPreviewIcon(ShopItemRecord item) {
        ItemStack icon = new ItemStack(safeMaterial(item.getMaterial()), 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + item.getId());
            ListString> lore = new ArrayList<>();
            lore.add("§7Kategori: §f" + item.getCategory());
            lore.add("§7Harga: §a" + String.format("%.2f", item.getCurrentPrice()));
            // Revisi 16: append the inflation/deflation explanation lines
            // whenever the current economic state has moved this item's
            // price away from its configured base price.
            InflationRecord latestInflation = EcoCorePlugin.getInstance().getInflationEngine().getLatestRecord();
            PriceDisplayHelper.DisplayPrices display = PriceDisplayHelper.resolve(item.getBasePrice(), latestInflation);
            lore.addAll(PriceDisplayHelper.buildPriceLoreLines(item.getBasePrice(), display));
            lore.add("§7Stock: §f" + item.getStock() + "/" + item.getMaxStock());
            lore.add(item.isSoldOut() ? "§c§lSELL OUT" : "§a§lTersedia");
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildBuyButton(ShopItemRecord item, int amount) {
        double total = item.getCurrentPrice() * amount;
        ItemStack icon = new ItemStack(Material.EMERALD, Math.min(amount, 64));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aBeli " + amount + "x");
            meta.setLore(List.of("§7Total: §a" + String.format("%.2f", total)));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildBuyMaxButton(ShopItemRecord item) {
        ItemStack icon = new ItemStack(Material.EMERALD_BLOCK, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lBeli Max (" + item.getStock() + "x)");
            meta.setLore(List.of("§7Total: §a"
                    + String.format("%.2f", item.getCurrentPrice() * item.getStock())));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Material.STONE;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        ShopItemRecord item = shopManager.getItem(itemId);
        if (item == null) {
            getViewer().closeInventory();
            return;
        }
        int amount = switch (slot) {
            case BUY_1_SLOT -> 1;
            case BUY_10_SLOT -> 10;
            case BUY_64_SLOT -> 64;
            case BUY_MAX_SLOT -> item.getStock();
            default -> 0;
        };
        if (amount > 0) {
            ShopBuyConfirmGui confirmGui = new ShopBuyConfirmGui(
                    getViewer(), shopManager, shopConfig, guiManager, guiConfig, messagesConfig, itemId, amount, this);
            guiManager.register(getViewer(), confirmGui);
            confirmGui.open();
            return;
        }
        if (slot == FAVORITE_SLOT) {
            shopManager.toggleFavorite(getViewer().getUniqueId(), itemId);
            render();
            return;
        }
        if (slot == BACK_SLOT) {
            getViewer().closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            getViewer().closeInventory();
        }
    }
}