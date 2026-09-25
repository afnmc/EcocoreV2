package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.economy.EconomyEngine;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * {@code /player} - shows a Minecraft player's current EcoCore balance.
 */
public final class PlayerSlashCommand implements SlashCommandHandler {

    private final EconomyEngine economyEngine;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the player command handler.
     *
     * @param economyEngine shared economy engine
     * @param embedBuilder  shared embed builder
     */
    public PlayerSlashCommand(EconomyEngine economyEngine, DiscordEmbedBuilder embedBuilder) {
        this.economyEngine = economyEngine;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "player";
    }

    @Override
    public CommandData build() {
        return Commands.slash("player", "Lihat info saldo seorang player")
                .addOptions(new OptionData(OptionType.STRING, "name", "Nama player Minecraft", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("name").getAsString();

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            event.reply("Player `" + playerName + "` tidak ditemukan.").setEphemeral(true).queue();
            return;
        }

        double balance = economyEngine.getBalance(offlinePlayer.getUniqueId());
        var embed = embedBuilder.infoEmbed(playerName,
                "Saldo: " + economyEngine.format(balance));
        event.replyEmbeds(embed.build()).queue();
    }
}