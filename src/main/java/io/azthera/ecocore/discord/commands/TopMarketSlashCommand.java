package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.ai.TrendAnalyzer;
import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /topmarket} - shows the items with the highest and lowest
 * 24-hour price trends.
 */
public final class TopMarketSlashCommand implements SlashCommandHandler {

    private static final long WINDOW_MILLIS = 24L * 60 * 60 * 1000;
    private static final int TOP_SIZE = 5;

    private final ShopManager shopManager;
    private final TrendAnalyzer trendAnalyzer;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the top-market command handler.
     *
     * @param shopManager   shared shop manager
     * @param trendAnalyzer shared trend analyzer
     * @param embedBuilder  shared embed builder
     */
    public TopMarketSlashCommand(ShopManager shopManager, TrendAnalyzer trendAnalyzer, DiscordEmbedBuilder embedBuilder) {
        this.shopManager = shopManager;
        this.trendAnalyzer = trendAnalyzer;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "topmarket";
    }

    @Override
    public CommandData build() {
        return Commands.slash("topmarket", "Lihat barang dengan tren harga tertinggi/terendah 24 jam terakhir");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        record Ranked(String itemId, double percentChange) {
        }

        List<Ranked> ranked = new ArrayList<>();
        for (ShopItemRecord item : shopManager.getAllItems()) {
            try {
                TrendAnalyzer.Trend trend = trendAnalyzer.computeTrend(item.getId(), WINDOW_MILLIS);
                if (trend.sampleCount() >= 2) {
                    ranked.add(new Ranked(item.getId(), trend.percentChange()));
                }
            } catch (java.sql.SQLException ignored) {
                // Skip items whose trend couldn't be computed this pass.
            }
        }

        List<String> rising = ranked.stream()
                .sorted(Comparator.comparingDouble(Ranked::percentChange).reversed())
                .limit(TOP_SIZE)
                .map(r -> "\uD83D\uDCC8 " + r.itemId() + " (+" + String.format("%.1f", r.percentChange()) + "%)")
                .toList();

        List<String> falling = ranked.stream()
                .sorted(Comparator.comparingDouble(Ranked::percentChange))
                .limit(TOP_SIZE)
                .map(r -> "\uD83D\uDCC9 " + r.itemId() + " (" + String.format("%.1f", r.percentChange()) + "%)")
                .toList();

        List<String> lines = new ArrayList<>();
        lines.add("**Naik:**");
        lines.addAll(rising.isEmpty() ? List.of("-") : rising);
        lines.add("");
        lines.add("**Turun:**");
        lines.addAll(falling.isEmpty() ? List.of("-") : falling);

        event.getHook().sendMessageEmbeds(
                embedBuilder.topMarketEmbed("Top Market (24 Jam)", lines).build()
        ).queue();
    }
}