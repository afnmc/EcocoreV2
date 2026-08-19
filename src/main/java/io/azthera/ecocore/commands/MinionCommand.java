package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.minions.MinionUpgradeGui;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /minion <id>} - opens the upgrade screen for a single minion
 * by its database id, provided the sender owns it.
 */
public final class MinionCommand implements CommandExecutor {

    private final MinionManager minionManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the minion command.
     *
     * @param minionManager shared minion manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public MinionCommand(MinionManager minionManager, ConfigManager configManager, GuiManager guiManager, GuiConfig guiConfig) {
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

        if (args.length < 1) {
            player.sendMessage("§cGunakan: /minion <id>. Lihat ID lewat /minions.");
            return true;
        }

        long minionId;
        try {
            minionId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            player.sendMessage("§cID minion tidak valid.");
            return true;
        }

        MinionData data = minionManager.getMinion(minionId);
        if (data == null || !data.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§cMinion tidak ditemukan atau bukan milik lu.");
            return true;
        }

        MinionUpgradeGui gui = new MinionUpgradeGui(player, minionManager, minionManager.getMinionsConfig(),
                guiManager, guiConfig, minionId, null);
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}