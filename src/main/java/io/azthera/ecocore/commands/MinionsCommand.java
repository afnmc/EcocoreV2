package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.minions.MinionsMainGui;
import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /minions} - opens the minions overview GUI listing every
 * minion the player currently owns.
 */
public final class MinionsCommand implements CommandExecutor {

    private final MinionManager minionManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the minions command.
     *
     * @param minionManager shared minion manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public MinionsCommand(MinionManager minionManager, ConfigManager configManager, GuiManager guiManager, GuiConfig guiConfig) {
        this.minionManager = minionManager;
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

        if (!configManager.isModuleEnabled("minions-enabled")) {
            player.sendMessage("§cFitur minions sedang dinonaktifkan.");
            return true;
        }

        MinionsMainGui gui = new MinionsMainGui(player, minionManager, minionManager.getMinionsConfig(), guiManager, guiConfig);
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}