package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.jobs.JobDetailGui;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code /job <name>} - opens the detail screen for a single job by name.
 */
public final class JobCommand implements CommandExecutor {

    private final JobsManager jobsManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the job command.
     *
     * @param jobsManager   shared jobs manager
     * @param configManager resolved main config manager
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public JobCommand(JobsManager jobsManager, ConfigManager configManager, GuiManager guiManager, GuiConfig guiConfig) {
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

        if (args.length < 1) {
            String available = Arrays.stream(JobType.values()).map(JobType::configKey)
                    .collect(Collectors.joining(", "));
            player.sendMessage("§cGunakan: /job <nama>. Pilihan: " + available);
            return true;
        }

        JobType type = JobType.fromConfigKey(args[0]);
        if (type == null) {
            player.sendMessage("§cJob '" + args[0] + "' tidak ditemukan.");
            return true;
        }

        try {
            if (!jobsManager.hasJoined(player.getUniqueId(), type)) {
                player.sendMessage("§cLu belum join job ini. Buka /jobs buat join.");
                return true;
            }
        } catch (SQLException exception) {
            player.sendMessage("§cGagal memuat data job.");
            return true;
        }

        JobDetailGui gui = new JobDetailGui(player, jobsManager, jobsManager.getJobsConfig(),
                guiManager, guiConfig, configManager.getMessagesConfig(), type);
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}