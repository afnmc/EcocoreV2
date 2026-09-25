package io.azthera.ecocore.gui.admin;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.GuiPage;
import io.azthera.ecocore.input.PrivateChatInputManager;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopCategory;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Admin-only shop editor.
 *
 * Flow:
 *  Shop Editor -> Category -> item editor
 *  Add Item -> close GUI -> hold the exact item -> enter price in private chat
 *  Add Category -> close GUI -> hold icon -> enter category name in private chat
 */
public final class AdminShopEditorGui extends AbstractGui {
    private final GuiManager guiManager;
    private final PrivateChatInputManager inputManager;
    private final String categoryId;
    private List<ShopItemRecord> items = new ArrayList<>();
    private GuiPage<ShopItemRecord> page;

    /** Root category browser. */
    public AdminShopEditorGui(Player viewer, GuiManager guiManager, PrivateChatInputManager inputManager) {
        this(viewer, guiManager, inputManager, null);
    }

    /** Category item editor. */
    private AdminShopEditorGui(Player viewer, GuiManager guiManager,
                               PrivateChatInputManager inputManager, String categoryId) {
        super(viewer);
        this.guiManager = guiManager;
        this.inputManager = inputManager;
        this.categoryId = categoryId;
    }

    @Override
    public void build() {
        if (categoryId == null) {
            buildCategories();
        } else {
            buildItems();
        }
    }

    private void buildCategories() {
        inventory = Bukkit.createInventory(this, 54, color("&8&lEcoCore &7» &eShop Categories"));
        List<ShopCategory> categories = new ArrayList<>(
                EcoCorePlugin.getInstance().getShopManager().getCategories().values());
        categories.sort(Comparator.comparing(ShopCategory::getId, String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < Math.min(45, categories.size()); i++) {
            ShopCategory category = categories.get(i);
            Material icon = ItemUtils.safeMaterial(category.getIcon());
            inventory.setItem(i, button(icon, category.getDisplayName(),
                    "&7ID: &f" + category.getId(),
                    "&7Items: &f" + category.getItems().size(),
                    "&eKlik untuk membuka"));
        }

        inventory.setItem(45, button(Material.NAME_TAG, "&a+ Tambah Category",
                "&7Pegang item yang mau dijadikan icon.",
                "&7Klik lalu GUI akan ditutup.",
                "&7Setelah itu masukkan nama category di chat."));
        inventory.setItem(48, button(Material.EMERALD, "&aReload Shop",
                "&7Muat ulang katalog dan category."));
        inventory.setItem(50, button(Material.ARROW, "&eKembali"));
    }

    private void buildItems() {
        ShopManager manager = EcoCorePlugin.getInstance().getShopManager();
        ShopCategory category = manager.getCategories().get(categoryId);
        String title = category == null ? categoryId : category.getDisplayName();

        inventory = Bukkit.createInventory(this, 54,
                color("&8&lShop Editor &7» &f" + ChatColor.stripColor(color(title))));
        items = category == null ? new ArrayList<>() : new ArrayList<>(category.getItems());
        items.sort(Comparator.comparing(ShopItemRecord::getId, String.CASE_INSENSITIVE_ORDER));
        page = new GuiPage<>(items, 45);

        renderItems();

        inventory.setItem(45, button(Material.EMERALD, "&a+ Tambah Item",
                "&7Pegang item yang ingin ditambahkan.",
                "&7Klik lalu GUI akan ditutup.",
                "&7Setelah itu masukkan harga di chat."));
        inventory.setItem(48, button(Material.COMPASS, "&bCari Item",
                "&7Cari item di category ini."));
        inventory.setItem(49, button(Material.ARROW, "&eKembali ke Category"));
        inventory.setItem(50, button(Material.CHEST, "&6Reload Catalog"));
        if (page.hasNextPage()) inventory.setItem(53, button(Material.ARROW, "&eBerikutnya"));
        if (page.hasPreviousPage()) inventory.setItem(47, button(Material.ARROW, "&eSebelumnya"));
    }

    private void renderItems() {
        for (int i = 0; i < 45; i++) inventory.setItem(i, null);
        List<ShopItemRecord> current = page.getCurrentPageItems();
        for (int i = 0; i < current.size(); i++) {
            inventory.setItem(i, icon(current.get(i)));
        }
    }

    private ItemStack icon(ShopItemRecord record) {
        ItemStack item = ItemUtils.createShopItem(record, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&f" + record.getId()));
            meta.setLore(List.of(
                    color("&7Harga: &a$" + String.format(Locale.US, "%.2f", record.getCurrentPrice())),
                    color("&7Stock: &f" + record.getStock() + "/" + record.getMaxStock()),
                    color("&7Material: &f" + record.getMaterial()),
                    color("&eKlik untuk edit harga")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(java.util.Arrays.stream(lore).map(this::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void openCategories() {
        AdminShopEditorGui gui = new AdminShopEditorGui(viewer, guiManager, inputManager);
        guiManager.register(viewer, gui);
        gui.open();
    }

    private void openCategory(String id) {
        AdminShopEditorGui gui = new AdminShopEditorGui(viewer, guiManager, inputManager, id);
        guiManager.register(viewer, gui);
        gui.open();
    }

    private void requestCategoryAdd() {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            viewer.sendMessage(color("&c[EcoCore] Pegang item icon category di tangan utama terlebih dahulu."));
            return;
        }

        final ItemStack icon = held.clone();
        viewer.closeInventory();
        viewer.sendMessage(color("&a[EcoCore] Icon category tersimpan sementara: &f" + icon.getType().name()));
        viewer.sendMessage(color("&fMasukkan nama category di chat."));
        viewer.sendMessage(color("&7Contoh: &fMining"));
        viewer.sendMessage(color("&7Ketik &fcancel &7untuk batal."));

        inputManager.request(viewer, name -> {
            String clean = ChatColor.stripColor(name).trim();
            if (clean.isBlank()) {
                viewer.sendMessage(color("&c[EcoCore] Nama category tidak boleh kosong."));
                openCategories();
                return;
            }
            String id = clean.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_-]+", "-")
                    .replaceAll("-{2,}", "-")
                    .replaceAll("^-|-$", "");
            if (id.isBlank()) {
                viewer.sendMessage(color("&c[EcoCore] Nama category tidak valid."));
                openCategories();
                return;
            }

            boolean ok = EcoCorePlugin.getInstance().getShopManager()
                    .addCategory(id, clean, icon.getType().name(),
                            ItemUtils.serialize(EcoCorePlugin.getInstance().getLogger(),
                                    new ItemStack[]{icon}));
            viewer.sendMessage(ok
                    ? color("&a[EcoCore] Category &f" + clean + " &aberhasil dibuat.")
                    : color("&c[EcoCore] Gagal membuat category. ID mungkin sudah ada."));
            openCategories();
        }, () -> openCategories());
    }

    private void requestItemAdd() {
        if (categoryId == null) return;

        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            viewer.sendMessage(color("&c[EcoCore] Pegang item yang mau ditambahkan di tangan utama terlebih dahulu."));
            return;
        }

        final ItemStack selected = held.clone();
        viewer.closeInventory();
        viewer.sendMessage(color("&a[EcoCore] Item yang dipilih: &f" + selected.getType().name()));
        viewer.sendMessage(color("&fMasukkan harga beli item di chat."));
        viewer.sendMessage(color("&7Contoh: &f500"));
        viewer.sendMessage(color("&7Ketik &fcancel &7untuk batal."));

        inputManager.request(viewer, input -> {
            try {
                double price = Double.parseDouble(input.replace(",", "").trim());
                if (!Double.isFinite(price) || price <= 0) throw new NumberFormatException();

                String id = EcoCorePlugin.getInstance().getShopManager()
                        .addItemFromHand(categoryId, selected, price);
                if (id == null) {
                    viewer.sendMessage(color("&c[EcoCore] Item gagal ditambahkan. Pastikan category valid dan item bukan item terlarang/admin."));
                } else {
                    viewer.sendMessage(color("&a[EcoCore] Item &f" + id + " &aberhasil ditambahkan ke category."));
                }
            } catch (NumberFormatException ex) {
                viewer.sendMessage(color("&c[EcoCore] Harga tidak valid."));
            }
            openCategory(categoryId);
        }, () -> openCategory(categoryId));
    }

    private void requestSearch() {
        if (categoryId == null) return;
        viewer.closeInventory();
        viewer.sendMessage(color("&b[EcoCore] Silakan ketik nama item yang mau dicari di chat."));
        viewer.sendMessage(color("&7Ketik &fcancel &7untuk batal."));

        inputManager.request(viewer, query -> {
            ShopCategory category = EcoCorePlugin.getInstance().getShopManager().getCategories().get(categoryId);
            List<ShopItemRecord> found = category == null ? List.of()
                    : category.getItems().stream()
                    .filter(item -> item.getId().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                            || item.getMaterial().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                    .toList();

            AdminShopEditorGui next = new AdminShopEditorGui(viewer, guiManager, inputManager, categoryId);
            guiManager.register(viewer, next);
            next.items = new ArrayList<>(found);
            next.page = new GuiPage<>(next.items, 45);
            next.inventory = Bukkit.createInventory(next, 54, next.color("&8&lShop Search &7» &f" + query));
            next.renderItems();
            next.inventory.setItem(45, next.button(Material.EMERALD, "&a+ Tambah Item"));
            next.inventory.setItem(49, next.button(Material.ARROW, "&eKembali"));
            if (next.page.hasNextPage()) next.inventory.setItem(53, next.button(Material.ARROW, "&eBerikutnya"));
            next.open();
        }, () -> openCategory(categoryId));
    }

    private void requestPriceEdit(ShopItemRecord item) {
        viewer.closeInventory();
        viewer.sendMessage(color("&e[EcoCore] Harga saat ini: &a$" + String.format(Locale.US, "%.2f", item.getCurrentPrice())));
        viewer.sendMessage(color("&fMasukkan harga baru untuk &b" + item.getId() + "&7 atau &fcancel&7."));

        inputManager.request(viewer, input -> {
            try {
                double price = Double.parseDouble(input.replace(",", "").trim());
                if (!Double.isFinite(price) || price <= 0) throw new NumberFormatException();
                boolean ok = EcoCorePlugin.getInstance().getShopManager().setCurrentPrice(item.getId(), price);
                viewer.sendMessage(ok
                        ? color("&a[EcoCore] Harga berhasil diubah.")
                        : color("&c[EcoCore] Gagal menyimpan harga."));
            } catch (NumberFormatException ex) {
                viewer.sendMessage(color("&c[EcoCore] Harga tidak valid."));
            }
            openCategory(categoryId);
        }, () -> openCategory(categoryId));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (categoryId == null) {
            if (slot == 45) { requestCategoryAdd(); return; }
            if (slot == 48) {
                EcoCorePlugin.getInstance().getShopManager().loadCatalog();
                viewer.sendMessage(color("&a[EcoCore] Shop catalog berhasil direload."));
                buildCategories();
                viewer.updateInventory();
                return;
            }
            if (slot == 50) {
                AdminMainGui gui = new AdminMainGui(viewer, guiManager, inputManager);
                guiManager.register(viewer, gui);
                gui.open();
                return;
            }
            if (slot >= 0 && slot < 45) {
                List<ShopCategory> categories = new ArrayList<>(
                        EcoCorePlugin.getInstance().getShopManager().getCategories().values());
                categories.sort(Comparator.comparing(ShopCategory::getId, String.CASE_INSENSITIVE_ORDER));
                if (slot < categories.size()) openCategory(categories.get(slot).getId());
            }
            return;
        }

        if (slot == 45) { requestItemAdd(); return; }
        if (slot == 48) { requestSearch(); return; }
        if (slot == 49) { openCategories(); return; }
        if (slot == 50) {
            EcoCorePlugin.getInstance().getShopManager().loadCatalog();
            viewer.sendMessage(color("&a[EcoCore] Catalog berhasil direload."));
            buildItems();
            viewer.updateInventory();
            return;
        }
        if (slot == 47 && page.hasPreviousPage()) { page.previousPage(); renderItems(); return; }
        if (slot == 53 && page.hasNextPage()) { page.nextPage(); renderItems(); return; }
        if (slot >= 0 && slot < 45) {
            List<ShopItemRecord> current = page.getCurrentPageItems();
            if (slot < current.size()) requestPriceEdit(current.get(slot));
        }
    }
}
