package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.jobs.JobsMainGui;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /jobs} - opens the jobs overview GUI.
 */
public final class JobsCommand implements CommandExecutor {

    private final JobsManager jobsManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the jobs command.
     *
     * @param jobsManager   shared jobs manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public JobsCommand(JobsManager jobsManager, ConfigManager configManager, GuiManager guiManager, GuiConfig guiConfig) {
        this.jobsManager = jobsManager;
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

        if (!configManager.isModuleEnabled("jobs-enabled")) {
            player.sendMessage("§cFitur jobs sedang dinonaktifkan.");
            return true;
        }

        JobsMainGui gui = new JobsMainGui(player, jobsManager, jobsManager.getJobsConfig(),
                guiManager, guiConfig, configManager.getMessagesConfig());
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}