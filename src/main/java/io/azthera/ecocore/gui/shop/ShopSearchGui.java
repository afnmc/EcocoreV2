package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.GuiPage;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Paginated shop search results. Input is collected privately through chat. */
public final class ShopSearchGui extends AbstractGui {
    private static final int RESULTS_PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int SEARCH_SLOT = 49;
    private static final int BACK_SLOT = 50;
    private static final int NEXT_SLOT = 53;

    private final ShopManager shopManager;
    private final ShopConfig shopConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;
    private GuiPage<ShopItemRecord> resultsPage;
    private String query = "";

    public ShopSearchGui(Player viewer, ShopManager shopManager, ShopConfig shopConfig,
                         GuiManager guiManager, GuiConfig guiConfig, MessagesConfig messagesConfig) {
        super(viewer);
        this.shopManager = shopManager;
        this.shopConfig = shopConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, color("&8&lShop &7» &f" + (query.isBlank() ? "Search" : query)));
        renderResults();
    }

    public void showResults(List<ShopItemRecord> results, String query) {
        this.query = query == null ? "" : query;
        this.resultsPage = new GuiPage<>(results == null ? List.of() : results, RESULTS_PER_PAGE);
        open();
    }

    private void renderResults() {
        if (resultsPage == null) resultsPage = new GuiPage<>(List.of(), RESULTS_PER_PAGE);
        for (int slot = 0; slot < RESULTS_PER_PAGE; slot++) inventory.setItem(slot, null);
        List<ShopItemRecord> current = resultsPage.getCurrentPageItems();
        for (int i = 0; i < current.size(); i++) inventory.setItem(i, buildItemIcon(current.get(i)));
        inventory.setItem(PREV_SLOT, resultsPage.hasPreviousPage() ? button("prev-page", "&eHalaman Sebelumnya") : null);
        inventory.setItem(SEARCH_SLOT, button("search", "&bCari Lagi"));
        inventory.setItem(BACK_SLOT, button("back", "&eKembali"));
        inventory.setItem(NEXT_SLOT, resultsPage.hasNextPage() ? button("next-page", "&eHalaman Berikutnya") : null);
    }

    private ItemStack buildItemIcon(ShopItemRecord item) {
        ItemStack icon = ItemUtils.createShopItem(item, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + item.getId());
            List<String> lore = new ArrayList<>();
            lore.add("§7Harga: §a$" + String.format("%.2f", item.getCurrentPrice()));
            lore.add("§7Stock: §f" + item.getStock() + "/" + item.getMaxStock());
            lore.add("§eKlik untuk melihat item");
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack button(String key, String name) { return guiManager.buildButtonIcon(key, color(name)); }
    private String color(String input) { return ChatColor.translateAlternateColorCodes('&', input); }

    private void requestSearch() {
        viewer.closeInventory();
        viewer.sendMessage(messagesConfig.getWithPrefix("ui.shop.search-prompt"));
        viewer.sendMessage(messagesConfig.get("ui.shop.search-example"));
        viewer.sendMessage(messagesConfig.get("ui.shop.search-cancel"));
        io.azthera.ecocore.EcoCorePlugin.getInstance().getPrivateChatInputManager().request(viewer, text -> {
            ShopSearchGui next = new ShopSearchGui(viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig);
            guiManager.register(viewer, next);
            next.showResults(shopManager.search(text), text);
        }, () -> { guiManager.register(viewer, this); open(); });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == SEARCH_SLOT) { requestSearch(); return; }
        if (slot == BACK_SLOT) {
            ShopMainGui main = new ShopMainGui(viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig);
            guiManager.register(viewer, main); main.open(); return;
        }
        if (resultsPage == null) return;
        if (slot == PREV_SLOT && resultsPage.hasPreviousPage()) { resultsPage.previousPage(); renderResults(); return; }
        if (slot == NEXT_SLOT && resultsPage.hasNextPage()) { resultsPage.nextPage(); renderResults(); return; }
        if (slot >= 0 && slot < RESULTS_PER_PAGE) {
            List<ShopItemRecord> current = resultsPage.getCurrentPageItems();
            if (slot < current.size()) {
                ShopItemRecord item = current.get(slot);
                ShopItemPreviewGui preview = new ShopItemPreviewGui(viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig, item.getId());
                guiManager.register(viewer, preview); preview.open();
            }
        }
    }
}
