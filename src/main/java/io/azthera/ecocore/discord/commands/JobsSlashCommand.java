package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobData;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.SQLException;
import java.util.List;

/**
 * {@code /jobs} - shows a player's progress across every job they've joined.
 */
public final class JobsSlashCommand implements SlashCommandHandler {

    private final JobsManager jobsManager;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the jobs command handler.
     *
     * @param jobsManager  shared jobs manager
     * @param embedBuilder shared embed builder
     */
    public JobsSlashCommand(JobsManager jobsManager, DiscordEmbedBuilder embedBuilder) {
        this.jobsManager = jobsManager;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "jobs";
    }

    @Override
    public CommandData build() {
        return Commands.slash("jobs", "Lihat progres jobs seorang player")
                .addOptions(new OptionData(OptionType.STRING, "player", "Nama player Minecraft", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        try {
            List<JobData> progress = jobsManager.getAllProgress(offlinePlayer.getUniqueId());
            if (progress.isEmpty()) {
                event.reply(playerName + " belum bergabung dengan job apapun.").setEphemeral(true).queue();
                return;
            }

            StringBuilder description = new StringBuilder();
            for (JobData data : progress) {
                description.append(data.getJobType().configKey())
                        .append(": Level ").append(data.getLevel())
                        .append(" (Prestige ").append(data.getPrestige()).append(")\n");
            }

            event.replyEmbeds(embedBuilder.infoEmbed("Jobs: " + playerName, description.toString()).build()).queue();
        } catch (SQLException exception) {
            event.reply("Gagal memuat data jobs.").setEphemeral(true).queue();
        }
    }
}