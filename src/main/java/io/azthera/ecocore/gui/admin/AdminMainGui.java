package io.azthera.ecocore.gui.admin;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.input.PrivateChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Central administrator dashboard for EcoCore and dynamically linked modules. */
public final class AdminMainGui extends AbstractGui {
    private final GuiManager guiManager;
    private final PrivateChatInputManager inputManager;

    public AdminMainGui(Player viewer, GuiManager guiManager, PrivateChatInputManager inputManager) {
        super(viewer);
        this.guiManager = guiManager;
        this.inputManager = inputManager;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, color("&8&lEcoCore &7» &cAdmin"));
        set(10, Material.GOLD_INGOT, "&6Economy", "&7Balance, money supply, statistics");
        set(11, Material.CHEST, "&eShop Editor", "&7Search and edit shop prices");
        set(12, Material.NETHER_STAR, "&dNight Market", "&7Market administration and rotation");
        set(13, Material.EXPERIENCE_BOTTLE, "&aInflation", "&7View current economic state");
        set(14, Material.REDSTONE, "&bAI Economy", "&7Run an AI pricing cycle");
        set(15, Material.HOPPER, "&9Restock", "&7Run a manual restock pass");
        set(16, Material.WRITABLE_BOOK, "&fTransactions", "&7Player transaction tools");
        set(19, Material.COMPARATOR, "&cConfiguration", "&7Reload EcoCore YAML configuration");
        set(20, Material.ENDER_CHEST, "&5Linked Plugins", linkedLore());
        set(49, Material.BARRIER, "&cTutup", "&7Close admin panel");
    }

    private List<String> linkedLore() {
        return List.of("&7Modules currently detected:", "&a✓ EcoCore", 
                Bukkit.getPluginManager().getPlugin("EcoJobs") != null ? "&a✓ EcoJobs" : "&8✗ EcoJobs (not installed)",
                Bukkit.getPluginManager().getPlugin("EcoMinions") != null ? "&a✓ EcoMinions" : "&8✗ EcoMinions (not installed)");
    }

    private void set(int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore == null ? List.of() : List.of(lore).stream().map(this::color).toList());
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private void set(int slot, Material material, String name, List<String> lore) {
        set(slot, material, name, lore.toArray(String[]::new));
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() >= inventory.getSize()) return;
        switch (event.getRawSlot()) {
            case 10 -> viewer.sendMessage(color("&6[EcoCore] &fEconomy: &7gunakan /balance atau pilih player tools di versi berikutnya."));
            case 11 -> openShopEditor();
            case 12 -> { viewer.closeInventory(); viewer.performCommand("market"); }
            case 13 -> { viewer.closeInventory(); viewer.performCommand("inflation"); }
            case 14 -> {
                EcoCorePlugin.getInstance().getAiLearningModel().invalidateCache();
                viewer.sendMessage(color("&a[EcoCore] Menjalankan siklus AI...") );
                EcoCorePlugin.getInstance().getAiEconomyEngine().runCycle();
                viewer.sendMessage(color("&a[EcoCore] Siklus AI selesai."));
                open();
            }
            case 15 -> {
                int count = EcoCorePlugin.getInstance().getRestockScheduler().runRestockPass(false, false).size();
                viewer.sendMessage(color("&a[EcoCore] Restock selesai: &f" + count + " &aitem."));
                open();
            }
            case 19 -> {
                EcoCorePlugin.getInstance().getConfigManager().reloadAll();
                EcoCorePlugin.getInstance().getShopManager().loadCatalog();
                viewer.sendMessage(EcoCorePlugin.getInstance().getMessagesConfig().getWithPrefix("general.reload-success"));
                open();
            }
            case 20 -> openLinkedPlugins();
            case 49 -> viewer.closeInventory();
        }
    }

    private void openShopEditor() {
        AdminShopEditorGui gui = new AdminShopEditorGui(viewer, guiManager, inputManager);
        guiManager.register(viewer, gui);
        gui.open();
    }

    private void openLinkedPlugins() {
        AdminLinkedPluginsGui gui = new AdminLinkedPluginsGui(viewer, guiManager, inputManager);
        guiManager.register(viewer, gui);
        gui.open();
    }
}
