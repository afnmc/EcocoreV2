package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.shop.ShopMainGui;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /shop} - opens the EcoCore shop GUI.
 */
public final class ShopCommand implements CommandExecutor {

    private final ShopManager shopManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the shop command.
     *
     * @param shopManager   shared shop manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public ShopCommand(ShopManager shopManager, ConfigManager configManager, GuiManager guiManager, GuiConfig guiConfig) {
        this.shopManager = shopManager;
        this.configManager = configManager;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini cuma bisa dipakai player.");
            return true;
        }

        if (!configManager.isModuleEnabled("shop-enabled")) {
            player.sendMessage("§cFitur shop sedang dinonaktifkan.");
            return true;
        }

        ShopMainGui gui = new ShopMainGui(player, shopManager, configManager.getShopConfig(),
                guiManager, guiConfig, configManager.getMessagesConfig());
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}