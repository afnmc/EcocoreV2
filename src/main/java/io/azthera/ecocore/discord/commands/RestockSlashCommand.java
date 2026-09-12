package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.shop.RestockScheduler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

/**
 * {@code /restock} - manually triggers a full restock pass. Restricted
 * to server members with Discord's "Manage Server" permission by
 * default, since it affects the live economy.
 */
public final class RestockSlashCommand implements SlashCommandHandler {

    private final RestockScheduler restockScheduler;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the restock command handler.
     *
     * @param restockScheduler shared restock scheduler
     * @param embedBuilder     shared embed builder
     */
    public RestockSlashCommand(RestockScheduler restockScheduler, DiscordEmbedBuilder embedBuilder) {
        this.restockScheduler = restockScheduler;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "restock";
    }

    @Override
    public CommandData build() {
        return Commands.slash("restock", "Jalankan restock manual untuk seluruh katalog")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        List<RestockScheduler.RestockOutcome> outcomes = restockScheduler.runRestockPass(false, false);

        String description = outcomes.isEmpty()
                ? "Tidak ada barang yang butuh restock saat ini."
                : outcomes.size() + " barang berhasil di-restock.";

        event.getHook().sendMessageEmbeds(
                embedBuilder.infoEmbed("Restock Manual", description).build()
        ).queue();
    }
}