package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.gui.graph.PriceGraphRenderer;
import io.azthera.ecocore.model.MarketSnapshot;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.database.dao.MarketHistoryDao;
import io.azthera.ecocore.shop.ShopManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.List;

/**
 * {@code /item} - shows full detail for a single shop item, including
 * a rendered 7-day price graph.
 */
public final class ItemSlashCommand implements SlashCommandHandler {

    private final ShopManager shopManager;
    private final MarketHistoryDao marketHistoryDao;
    private final PriceGraphRenderer graphRenderer;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the item command handler.
     *
     * @param shopManager      shared shop manager
     * @param marketHistoryDao DAO for price history snapshots
     * @param graphRenderer    shared price graph renderer
     * @param embedBuilder     shared embed builder
     */
    public ItemSlashCommand(ShopManager shopManager, MarketHistoryDao marketHistoryDao,
                             PriceGraphRenderer graphRenderer, DiscordEmbedBuilder embedBuilder) {
        this.shopManager = shopManager;
        this.marketHistoryDao = marketHistoryDao;
        this.graphRenderer = graphRenderer;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "item";
    }

    @Override
    public CommandData build() {
        return Commands.slash("item", "Lihat detail sebuah barang")
                .addOptions(new OptionData(OptionType.STRING, "id", "ID barang", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String itemId = event.getOption("id").getAsString();
        ShopItemRecord item = shopManager.getItem(itemId);

        if (item == null) {
            event.reply("Barang `" + itemId + "` tidak ditemukan.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        var embed = embedBuilder.infoEmbed(item.getId(),
                "Kategori: " + item.getCategory()
                        + "\nHarga: " + String.format("%.2f", item.getCurrentPrice())
                        + "\nStock: " + item.getStock() + "/" + item.getMaxStock()
                        + "\nStatus: " + (item.isSoldOut() ? "Sell Out" : "Tersedia"));

        try {
            long since = System.currentTimeMillis() - graphRenderer.resolvePeriodMillis("7d");
            List<MarketSnapshot> snapshots = marketHistoryDao.findSince(itemId, since);
            byte[] chart = graphRenderer.renderPriceChart(itemId, "7d", snapshots);

            if (chart != null) {
                embed.setImage("attachment://graph.png");
                event.getHook().sendFiles(FileUpload.fromData(chart, "graph.png"))
                        .addEmbeds(embed.build())
                        .queue();
                return;
            }
        } catch (java.sql.SQLException ignored) {
            // Fall through to sending without a graph.
        }

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}