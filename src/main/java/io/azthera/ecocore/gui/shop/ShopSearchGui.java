package io.azthera.ecocore.gui.shop;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.GuiPage;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Anvil-based free-text item search: the player renames a paper item
 * to their search query and clicks the output slot to run it, then
 * this same screen switches into a paginated results view.
 */
public final class ShopSearchGui extends AbstractGui {

    private static final int RESULTS_PER_PAGE = 45;
    private static final int RESULTS_PREV_SLOT = 45;
    private static final int RESULTS_CLOSE_SLOT = 49;
    private static final int RESULTS_NEXT_SLOT = 53;

    private final ShopManager shopManager;
    private final ShopConfig shopConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;

    private boolean showingResults = false;
    private GuiPage<ShopItemRecord> resultsPage;

    /**
     * Creates the search screen.
     *
     * @param viewer         the viewing player
     * @param shopManager    shared shop manager
     * @param shopConfig     resolved shop.yml configuration
     * @param guiManager     shared GUI manager
     * @param guiConfig      resolved gui.yml configuration
     * @param messagesConfig resolved messages.yml configuration
     */
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
        showingResults = false;
        inventory = Bukkit.createInventory(this, InventoryType.ANVIL, "§8Cari Barang");

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Ketik nama barang...");
            paper.setItemMeta(meta);
        }
        inventory.setItem(0, paper);
    }

    private void buildResults(List<ShopItemRecord> results) {
        showingResults = true;
        resultsPage = new GuiPage<>(results, RESULTS_PER_PAGE);
        inventory = Bukkit.createInventory(this, 54, "§8Hasil Pencarian");
        renderResults();
    }

    private void renderResults() {
        for (int slot = 0; slot < RESULTS_PER_PAGE; slot++) {
            inventory.setItem(slot, null);
        }

        List<ShopItemRecord> currentItems = resultsPage.getCurrentPageItems();
        for (int i = 0; i < currentItems.size(); i++) {
            inventory.setItem(i, buildItemIcon(currentItems.get(i)));
        }

        inventory.setItem(RESULTS_PREV_SLOT, resultsPage.hasPreviousPage()
                ? guiManager.buildButtonIcon("prev-page", "§eHalaman Sebelumnya") : null);
        inventory.setItem(RESULTS_CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
        inventory.setItem(RESULTS_NEXT_SLOT, resultsPage.hasNextPage()
                ? guiManager.buildButtonIcon("next-page", "§eHalaman Berikutnya") : null);
    }

    private ItemStack buildItemIcon(ShopItemRecord item) {
        Material material = safeMaterial(item.getMaterial());
        ItemStack icon = new ItemStack(material, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + item.getId());
            meta.setLore(List.of(
                    "§7Harga: §a" + String.format("%.2f", item.getCurrentPrice()),
                    "§7Stock: §f" + item.getStock() + "/" + item.getMaxStock()
            ));
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

        if (!showingResults) {
            if (event.getRawSlot() == 2 && inventory instanceof AnvilInventory anvilInventory) {
                String query = anvilInventory.getRenameText();
                if (query == null || query.isBlank()) {
                    return;
                }
                buildResults(shopManager.search(query));
                viewer.openInventory(inventory);
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot == RESULTS_PREV_SLOT && resultsPage.hasPreviousPage()) {
            resultsPage.previousPage();
            renderResults();
            return;
        }
        if (slot == RESULTS_NEXT_SLOT && resultsPage.hasNextPage()) {
            resultsPage.nextPage();
            renderResults();
            return;
        }
        if (slot == RESULTS_CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        if (slot >= 0 && slot < RESULTS_PER_PAGE) {
            List<ShopItemRecord> currentItems = resultsPage.getCurrentPageItems();
            if (slot < currentItems.size()) {
                ShopItemRecord selected = currentItems.get(slot);
                ShopItemPreviewGui previewGui = new ShopItemPreviewGui(
                        viewer, shopManager, shopConfig, guiManager, guiConfig, messagesConfig, selected.getId());
                guiManager.register(viewer, previewGui);
                previewGui.open();
            }
        }
    }
}