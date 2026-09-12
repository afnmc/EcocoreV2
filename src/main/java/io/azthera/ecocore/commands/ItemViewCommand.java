package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.shop.ShopItemPreviewGui;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /ecoitem <id>} - directly opens the buy-preview screen for a
 * single item. Mainly used as the click-target for chat components
 * produced by {@code /market} and Discord, since a chat message can't
 * open a GUI on its own.
 */
public final class ItemViewCommand implements CommandExecutor {

    private final ShopManager shopManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the item-view command.
     *
     * @param shopManager   shared shop manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public ItemViewCommand(ShopManager shopManager, ConfigManager configManager, GuiManager guiManager, GuiConfig guiConfig) {
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
        if (args.length < 1) {
            player.sendMessage("§cGunakan: /ecoitem <id>");
            return true;
        }

        ShopItemRecord item = shopManager.getItem(args[0]);
        if (item == null) {
            player.sendMessage("§cBarang '" + args[0] + "' tidak ditemukan.");
            return true;
        }

        ShopItemPreviewGui gui = new ShopItemPreviewGui(player, shopManager, configManager.getShopConfig(),
                guiManager, guiConfig, configManager.getMessagesConfig(), item.getId());
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}
