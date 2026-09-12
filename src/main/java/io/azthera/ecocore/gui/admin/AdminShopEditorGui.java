package io.azthera.ecocore.gui.admin;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.GuiPage;
import io.azthera.ecocore.input.PrivateChatInputManager;
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

/** Shop administration browser with private chat search and price editing. */
public final class AdminShopEditorGui extends AbstractGui {
    private final GuiManager guiManager;
    private final PrivateChatInputManager inputManager;
    private List<ShopItemRecord> items;
    private GuiPage<ShopItemRecord> page;
    private boolean resultsMode;

    public AdminShopEditorGui(Player viewer, GuiManager guiManager, PrivateChatInputManager inputManager) {
        super(viewer);
        this.guiManager = guiManager;
        this.inputManager = inputManager;
    }

    @Override
    public void build() {
        resultsMode = false;
        inventory = Bukkit.createInventory(this, 54, color("&8&lEcoCore &7» &eShop Editor"));
        inventory.setItem(49, button(Material.COMPASS, "&bCari Item", "&7Ketik nama/material di chat privat"));
        inventory.setItem(50, button(Material.ARROW, "&eKembali", "&7Ke admin dashboard"));
        inventory.setItem(48, button(Material.EMERALD, "&aReload Catalog", "&7Muat ulang shop-items.yml"));
    }

    public void showSearchResults(List<ShopItemRecord> found, String query) {
        resultsMode = true;
        items = found;
        page = new GuiPage<>(found, 45);
        inventory = Bukkit.createInventory(this, 54, color("&8&lShop Editor &7» &f" + query));
        render();
        viewer.openInventory(inventory);
    }

    private void render() {
        for (int i = 0; i < 45; i++) inventory.setItem(i, null);
        List<ShopItemRecord> current = page.getCurrentPageItems();
        for (int i = 0; i < current.size(); i++) inventory.setItem(i, icon(current.get(i)));
        inventory.setItem(45, page.hasPreviousPage() ? button(Material.ARROW, "&eSebelumnya") : null);
        inventory.setItem(49, button(Material.COMPASS, "&bCari Lagi", "&7Cari item lain"));
        inventory.setItem(50, button(Material.ARROW, "&eKembali"));
        inventory.setItem(53, page.hasNextPage() ? button(Material.ARROW, "&eBerikutnya") : null);
    }

    private ItemStack icon(ShopItemRecord record) {
        ItemStack item = ItemUtils.createShopItem(record, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&f" + record.getId()));
            meta.setLore(List.of(color("&7Harga: &a$" + String.format("%.2f", record.getCurrentPrice())),
                    color("&7Base: &f$" + String.format("%.2f", record.getBasePrice())),
                    color("&7Stock: &f" + record.getStock() + "/" + record.getMaxStock()),
                    color("&eKlik untuk edit harga")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(color(name)); meta.setLore(java.util.Arrays.stream(lore).map(this::color).toList()); item.setItemMeta(meta); }
        return item;
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private void requestSearch() {
        viewer.closeInventory();
        viewer.sendMessage(color("&b[EcoCore] &fSilakan ketik nama item yang mau dicari di chat."));
        viewer.sendMessage(color("&7Contoh: &fdiamond&7, dirt, enchanted book"));
        viewer.sendMessage(color("&7Ketik &fcancel &7untuk batal."));
        inputManager.request(viewer, query -> {
            AdminShopEditorGui next = new AdminShopEditorGui(viewer, guiManager, inputManager);
            guiManager.register(viewer, next);
            List<ShopItemRecord> found = EcoCorePlugin.getInstance().getShopManager().search(query);
            next.showSearchResults(found, query);
        }, () -> { guiManager.register(viewer, this); open(); });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 49) { requestSearch(); return; }
        if (slot == 50) { AdminMainGui gui = new AdminMainGui(viewer, guiManager, inputManager); guiManager.register(viewer, gui); gui.open(); return; }
        if (slot == 48 && !resultsMode) {
            EcoCorePlugin.getInstance().getShopManager().loadCatalog();
            viewer.sendMessage(color("&a[EcoCore] Shop catalog berhasil direload.")); open(); return;
        }
        if (!resultsMode) return;
        if (slot == 45 && page.hasPreviousPage()) { page.previousPage(); render(); return; }
        if (slot == 53 && page.hasNextPage()) { page.nextPage(); render(); return; }
        if (slot >= 0 && slot < 45) {
            List<ShopItemRecord> current = page.getCurrentPageItems();
            if (slot < current.size()) requestPriceEdit(current.get(slot));
        }
    }

    private void requestPriceEdit(ShopItemRecord item) {
        viewer.closeInventory();
        viewer.sendMessage(color("&e[EcoCore] &fHarga saat ini &a$" + String.format("%.2f", item.getCurrentPrice())));
        viewer.sendMessage(color("&fKetik harga baru untuk &b" + item.getId() + " &7atau &fcancel&7."));
        inputManager.request(viewer, input -> {
            try {
                double price = Double.parseDouble(input.replace(",", ""));
                if (!Double.isFinite(price) || price <= 0) throw new NumberFormatException();
                EcoCorePlugin.getInstance().getShopManager().setCurrentPrice(item.getId(), price);
                viewer.sendMessage(color("&a[EcoCore] Harga " + item.getId() + " diubah menjadi $" + String.format("%.2f", price)));
            } catch (NumberFormatException ex) {
                viewer.sendMessage(color("&c[EcoCore] Harga tidak valid."));
            }
            guiManager.register(viewer, this);
            List<ShopItemRecord> found = EcoCorePlugin.getInstance().getShopManager().search(item.getId());
            showSearchResults(found, item.getId());
        }, () -> { guiManager.register(viewer, this); showSearchResults(List.of(item), item.getId()); });
    }
}
