package io.azthera.ecocore.commands;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.admin.AdminMainGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Dedicated administrator command executor for {@code /ecoadmin} and {@code /ecocore:admin}.
 * Strictly requires the {@code ecocore.admin} permission and opens the central Admin GUI.
 */
public final class EcoAdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecocore.admin")) {
            sender.sendMessage(ChatColor.RED + "Lu gak punya izin buat command ini.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Admin Control Panel hanya bisa dibuka oleh player di dalam game.");
            return true;
        }

        AdminMainGui gui = new AdminMainGui(player,
                EcoCorePlugin.getInstance().getGuiManager(),
                EcoCorePlugin.getInstance().getPrivateChatInputManager());
        EcoCorePlugin.getInstance().getGuiManager().register(player, gui);
        gui.open();
        return true;
    }
}
