package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.market.NightMarketGui;
import io.azthera.ecocore.market.NightMarketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /market} - opens EcoCore's night market screen.
 */
public final class MarketCommand implements CommandExecutor {

    private final NightMarketManager nightMarketManager;
    private final GuiManager guiManager;
    private final ConfigManager configManager;

    /**
     * Creates the market command.
     *
     * @param nightMarketManager shared night market manager
     * @param guiManager         shared GUI manager
     * @param configManager      resolved main config manager
     */
    public MarketCommand(NightMarketManager nightMarketManager, GuiManager guiManager, ConfigManager configManager) {
        this.nightMarketManager = nightMarketManager;
        this.guiManager = guiManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini cuma bisa dipakai player.");
            return true;
        }

        NightMarketGui gui = new NightMarketGui(player, nightMarketManager, guiManager, configManager.getMessagesConfig());
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}
