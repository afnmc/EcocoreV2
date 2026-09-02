package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.sell.SellMainGui;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /sell} - opens the sell GUI, where players deposit items
 * into a dedicated area and sell them with a button.
 */
public final class SellCommand implements CommandExecutor {

    private final SellManager sellManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;

    /**
     * Creates the sell command.
     *
     * @param sellManager   shared sell manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     */
    public SellCommand(SellManager sellManager, ConfigManager configManager, GuiManager guiManager) {
        this.sellManager = sellManager;
        this.configManager = configManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini cuma bisa dipakai player.");
            return true;
        }

        if (!configManager.isModuleEnabled("sell-enabled")) {
            player.sendMessage("§cFitur sell sedang dinonaktifkan.");
            return true;
        }

        SellMainGui gui = new SellMainGui(player, sellManager, guiManager, configManager.getMessagesConfig());
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}