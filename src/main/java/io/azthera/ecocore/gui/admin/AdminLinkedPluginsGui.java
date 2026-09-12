package io.azthera.ecocore.gui.admin;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.input.PrivateChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Dynamically displays only installed Eco* module plugins. */
public final class AdminLinkedPluginsGui extends AbstractGui {
    private final GuiManager guiManager;
    private final PrivateChatInputManager inputManager;

    public AdminLinkedPluginsGui(Player viewer, GuiManager guiManager, PrivateChatInputManager inputManager) {
        super(viewer);
        this.guiManager = guiManager;
        this.inputManager = inputManager;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, color("&8&lEcoCore &7» &5Linked Plugins"));
        int slot = 10;
        set(slot++, Material.GOLD_BLOCK, "&aEcoCore", "&7Core economy module", "&aInstalled");
        String[] modules = {"EcoJobs", "EcoMinions", "EcoBank", "EcoAuction", "EcoCrates", "EcoMarket"};
        Material[] icons = {Material.EXPERIENCE_BOTTLE, Material.IRON_GOLEM_SPAWN_EGG, Material.EMERALD, Material.CHEST_MINECART, Material.CHEST, Material.NETHER_STAR};
        for (int i = 0; i < modules.length; i++) {
            String name = modules[i];
            if (Bukkit.getPluginManager().getPlugin(name) == null) continue;
            set(slot++, icons[i], "&b" + name, "&7Linked module", "&aInstalled", "&eManagement menu available");
        }
        inventory.setItem(49, button(Material.ARROW, "&eKembali"));
    }

    private void set(int slot, Material material, String name, String... lore) {
        inventory.setItem(slot, button(material, name, lore));
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

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() != 49) return;
        AdminMainGui gui = new AdminMainGui(viewer, guiManager, inputManager);
        guiManager.register(viewer, gui);
        gui.open();
    }
}
