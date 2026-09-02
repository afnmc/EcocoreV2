package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.GuiPage;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopCategory;
import io.azthera.ecocore.shop.ShopManager;
import io.azthera.ecocore.shop.ShopSortEngine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every item in a single shop category, with pagination,
 * cyclable sorting, favorite toggling (shift-click), and stock/
 * inflation indicators.
 */
public final class ShopCategoryGui extends AbstractGui {

    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int SORT_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    private final ShopManager shopManager;
    private final ShopConfig shopConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;
    private final String categoryId;

    private List<ShopItemRecord> categoryItems = new ArrayList<>();
    private GuiPage<ShopItemRecord> page;
    private ShopSortEngine.SortMode sortMode = ShopSortEngine.SortMode.NAME_ASC;

    /**
     * Creates a category listing screen.
     *
     * @param viewer         the viewing player
     * @param shopManager    shared shop manager
     * @param shopConfig     resolved shop.yml configuration
     * @param guiManager     shared GUI manager
     * @param guiConfig      resolved gui.yml configuration
     * @param messagesConfig resolved messages.yml configuration
     * @param categoryId     the category id to display
     */
    public ShopCategoryGui(Player viewer, ShopManager shopManager, ShopConfig shopConfig, GuiManager guiManager,
                            GuiConfig guiConfig, MessagesConfig messagesConfig, String categoryId) {
        super(viewer);
        this.shopManager = shopManager;
        this.shopConfig = shopConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
        this.categoryId = categoryId;
    }

    @Override
    public void build() {
        ShopCategory category = shopManager.getCategories().get(categoryId);
        String title = category != null ? stripColor(category.getDisplayName()) : "Shop";
        inventory = Bukkit.createInventory(this, shopConfig.getGuiRows() * 9, "§8" + title);

        categoryItems = new ArrayList<>(category != null ? category.getItems() : List.of());
        shopManager.sort(categoryItems, sortMode);
        page = new GuiPage<>(categoryItems, shopConfig.getItemsPerPage());
        render();
    }

    /**
     * Repopulates the already-created {@link #inventory} in place -
     * used for pagination AND sort changes, so the window on the
     * player's screen always stays the actively-tracked one.
     */
    private void render() {
        for (int slot = 0; slot < shopConfig.getItemsPerPage(); slot++) {
            inventory.setItem(slot, null);
        }

        List<ShopItemRecord> currentItems = page.getCurrentPageItems();
        for (int i = 0; i < currentItems.size(); i++) {
            inventory.setItem(i, buildItemIcon(currentItems.get(i)));
        }

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
        inventory.setItem(PREV_SLOT, page.hasPreviousPage()
                ? guiManager.buildButtonIcon("prev-page", "§eHalaman Sebelumnya") : null);
        inventory.setItem(SORT_SLOT, guiManager.buildButtonIcon("sort", "§bUrutkan: " + sortMode.name()));
        inventory.setItem(NEXT_SLOT, page.hasNextPage()
                ? guiManager.buildButtonIcon("next-page", "§eHalaman Berikutnya") : null);
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildItemIcon(ShopItemRecord item) {
        Material material = safeMaterial(item.getMaterial());
        ItemStack icon = new ItemStack(material, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((item.isSoldOut() ? "§c" : "§f") + item.getId());

            List<String> lore = new ArrayList<>();
            lore.add("§7Harga: §a" + String.format("%.2f", item.getCurrentPrice()));
            // Revisi 16: show the inflation/deflation delta explanation on
            // every item's shop-listing lore, not just the detail preview.
            io.azthera.ecocore.model.InflationRecord latestInflation =
                    io.azthera.ecocore.EcoCorePlugin.getInstance().getInflationEngine().getLatestRecord();
            io.azthera.ecocore.inflation.PriceDisplayHelper.DisplayPrices display =
                    io.azthera.ecocore.inflation.PriceDisplayHelper.resolve(item.getBasePrice(), latestInflation);
            lore.addAll(io.azthera.ecocore.inflation.PriceDisplayHelper.buildPriceLoreLines(item.getBasePrice(), display));
            if (shopConfig.isStockIndicatorEnabled()) {
                lore.add("§7Stock: §f" + item.getStock() + "/" + item.getMaxStock());
                if (item.isSoldOut()) {
                    lore.add("§c§lSELL OUT");
                } else if (item.stockPercent() <= shopConfig.getCriticalStockThresholdPercent()) {
                    lore.add("§c§lStok Kritis");
                } else if (item.stockPercent() <= shopConfig.getLowStockThresholdPercent()) {
                    lore.add("§e§lStok Menipis");
                }
            }
            if (shopManager.isFavorite(viewer.getUniqueId(), item.getId())) {
                lore.add("§d★ Favorit");
            }
            lore.add("§8Shift-klik untuk favorit");
            meta.setLore(lore);
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

    private String stripColor(String input) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', input));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            ShopMainGui mainGui = new ShopMainGui(viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig);
            guiManager.register(viewer, mainGui);
            mainGui.open();
            return;
        }

        if (slot == PREV_SLOT && page.hasPreviousPage()) {
            page.previousPage();
            render();
            return;
        }

        if (slot == NEXT_SLOT && page.hasNextPage()) {
            page.nextPage();
            render();
            return;
        }

        if (slot == SORT_SLOT && shopConfig.isSortingEnabled()) {
            sortMode = shopManager.getSortEngine().next(sortMode);
            categoryItems = shopManager.sort(categoryItems, sortMode);
            page = new GuiPage<>(categoryItems, shopConfig.getItemsPerPage());
            render();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        if (slot >= 0 && slot < shopConfig.getItemsPerPage()) {
            List<ShopItemRecord> currentItems = page.getCurrentPageItems();
            if (slot >= currentItems.size()) {
                return;
            }
            ShopItemRecord selected = currentItems.get(slot);

            if (event.isShiftClick() && shopConfig.isFavoriteEnabled()) {
                shopManager.toggleFavorite(viewer.getUniqueId(), selected.getId());
                render();
                return;
            }

            ShopItemPreviewGui previewGui = new ShopItemPreviewGui(
                    viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig, selected.getId());
            guiManager.register(viewer, previewGui);
            previewGui.open();
        }
    }
    }