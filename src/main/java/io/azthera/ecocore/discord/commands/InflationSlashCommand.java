package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.model.InflationRecord;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * {@code /inflation} - shows the server's current macro-economic state.
 */
public final class InflationSlashCommand implements SlashCommandHandler {

    private final InflationEngine inflationEngine;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the inflation command handler.
     *
     * @param inflationEngine shared inflation engine
     * @param embedBuilder    shared embed builder
     */
    public InflationSlashCommand(InflationEngine inflationEngine, DiscordEmbedBuilder embedBuilder) {
        this.inflationEngine = inflationEngine;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "inflation";
    }

    @Override
    public CommandData build() {
        return Commands.slash("inflation", "Lihat status ekonomi server saat ini");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        InflationRecord record = inflationEngine.getLatestRecord();
        if (record == null) {
            event.reply("Data inflasi belum tersedia.").setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(embedBuilder.inflationStateEmbed(record).build()).queue();
    }
}