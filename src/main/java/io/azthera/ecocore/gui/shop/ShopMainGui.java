package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.shop.ShopCategory;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * The root {@code /shop} screen: one clickable icon per configured
 * category, plus search/history/close controls in the bottom row.
 */
public final class ShopMainGui extends AbstractGui {

    private static final int SEARCH_SLOT = 48;
    private static final int HISTORY_SLOT = 50;
    private static final int CLOSE_SLOT = 49;

    private final ShopManager shopManager;
    private final ShopConfig shopConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;

    /**
     * Creates the shop main screen.
     *
     * @param viewer         the viewing player
     * @param shopManager    shared shop manager
     * @param shopConfig     resolved shop.yml configuration
     * @param guiManager     shared GUI manager
     * @param guiConfig      resolved gui.yml configuration
     * @param messagesConfig resolved messages.yml configuration, threaded through to child screens
     */
    public ShopMainGui(Player viewer, ShopManager shopManager, ShopConfig shopConfig,
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
        String title = translate(shopConfig.getGuiTitle());
        inventory = Bukkit.createInventory(this, shopConfig.getGuiRows() * 9, title);

        for (Map.Entry<String, ShopCategory> entry : shopManager.getCategories().entrySet()) {
            ShopCategory category = entry.getValue();
            inventory.setItem(category.getSlot(), buildCategoryIcon(category));
        }

        if (shopConfig.isSearchEnabled()) {
            inventory.setItem(SEARCH_SLOT, guiManager.buildButtonIcon("search", "§bCari Barang"));
        }
        if (shopConfig.isHistoryEnabled()) {
            inventory.setItem(HISTORY_SLOT, guiManager.buildButtonIcon("back", "§eRiwayat Transaksi"));
        }
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildCategoryIcon(ShopCategory category) {
        Material material = safeMaterial(category.getIcon());
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(category.getDisplayName()));
            meta.setLore(List.of("§7" + category.getItems().size() + " barang"));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Material.CHEST;
        }
    }

    private String translate(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == SEARCH_SLOT && shopConfig.isSearchEnabled()) {
            ShopSearchGui searchGui = new ShopSearchGui(
                    viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig);
            guiManager.register(viewer, searchGui);
            searchGui.open();
            return;
        }

        if (slot == HISTORY_SLOT && shopConfig.isHistoryEnabled()) {
            ShopHistoryGui historyGui = new ShopHistoryGui(viewer, shopManager, guiManager);
            guiManager.register(viewer, historyGui);
            historyGui.open();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        for (ShopCategory category : shopManager.getCategories().values()) {
            if (category.getSlot() == slot) {
                ShopCategoryGui categoryGui = new ShopCategoryGui(
                        viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig, category.getId());
                guiManager.register(viewer, categoryGui);
                categoryGui.open();
                return;
            }
        }
    }
}