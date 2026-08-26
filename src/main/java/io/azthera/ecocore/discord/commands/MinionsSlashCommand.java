package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /minions} - shows a summary of a player's placed minions.
 */
public final class MinionsSlashCommand implements SlashCommandHandler {

    private final MinionManager minionManager;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the minions command handler.
     *
     * @param minionManager shared minion manager
     * @param embedBuilder  shared embed builder
     */
    public MinionsSlashCommand(MinionManager minionManager, DiscordEmbedBuilder embedBuilder) {
        this.minionManager = minionManager;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "minions";
    }

    @Override
    public CommandData build() {
        return Commands.slash("minions", "Lihat ringkasan minion seorang player")
                .addOptions(new OptionData(OptionType.STRING, "player", "Nama player Minecraft", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        List<MinionData> minions = minionManager.getMinionsOwnedBy(offlinePlayer.getUniqueId());
        if (minions.isEmpty()) {
            event.reply(playerName + " belum memiliki minion.").setEphemeral(true).queue();
            return;
        }

        Map<String, Long> countByType = minions.stream()
                .collect(Collectors.groupingBy(m -> m.getType().configKey(), Collectors.counting()));

        StringBuilder description = new StringBuilder("Total minion: " + minions.size() + "\n\n");
        countByType.forEach((type, count) -> description.append(type).append(": ").append(count).append("\n"));

        event.replyEmbeds(embedBuilder.infoEmbed("Minions: " + playerName, description.toString()).build()).queue();
    }
}