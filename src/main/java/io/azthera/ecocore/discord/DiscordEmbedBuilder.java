package io.azthera.ecocore.discord;

import io.azthera.ecocore.config.DiscordConfig;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.model.ShopItemRecord;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Builds styled {@link EmbedBuilder} objects for every kind of
 * message EcoCore posts to Discord, using the colors and footer
 * text configured in {@code discord.yml}.
 */
public final class DiscordEmbedBuilder {

    private final DiscordConfig discordConfig;

    /**
     * Creates an embed builder helper.
     *
     * @param discordConfig resolved discord.yml configuration (colors, footer)
     */
    public DiscordEmbedBuilder(DiscordConfig discordConfig) {
        this.discordConfig = discordConfig;
    }

    private EmbedBuilder base() {
        return new EmbedBuilder()
                .setFooter(discordConfig.getFooterText())
                .setTimestamp(Instant.now());
    }

    private Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException exception) {
            return Color.LIGHT_GRAY;
        }
    }

    /**
     * Builds an embed announcing a single item's price change.
     *
     * @param item          the item whose price changed
     * @param previousPrice the price before this change
     * @return the built embed
     */
    public EmbedBuilder priceChangeEmbed(ShopItemRecord item, double previousPrice) {
        boolean up = item.getCurrentPrice() >= previousPrice;
        EmbedBuilder embed = base()
                .setTitle((up ? "\uD83D\uDCC8 " : "\uD83D\uDCC9 ") + item.getId())
                .setColor(parseColor(up ? discordConfig.getColorPriceUp() : discordConfig.getColorPriceDown()))
                .addField("Harga Sebelumnya", String.format("%.2f", previousPrice), true)
                .addField("Harga Sekarang", String.format("%.2f", item.getCurrentPrice()), true)
                .addField("Stock", item.getStock() + "/" + item.getMaxStock(), true);
        return embed;
    }

    /**
     * Builds an embed announcing an item going out of stock.
     *
     * @param item the item that sold out
     * @return the built embed
     */
    public EmbedBuilder sellOutEmbed(ShopItemRecord item) {
        return base()
                .setTitle("\u26A0\uFE0F Sell Out: " + item.getId())
                .setColor(parseColor(discordConfig.getColorCrash()))
                .setDescription("Barang ini kehabisan stock dan sementara tidak bisa dibeli.");
    }

    /**
     * Builds an embed announcing an item restock.
     *
     * @param item   the item that was restocked
     * @param amount the amount restocked
     * @return the built embed
     */
    public EmbedBuilder restockEmbed(ShopItemRecord item, int amount) {
        return base()
                .setTitle("\uD83D\uDCE6 Restock: " + item.getId())
                .setColor(parseColor(discordConfig.getColorPriceUp()))
                .addField("Ditambahkan", String.valueOf(amount), true)
                .addField("Stock Sekarang", item.getStock() + "/" + item.getMaxStock(), true);
    }

    /**
     * Builds an embed announcing an economic state change.
     *
     * @param record the newly computed inflation record
     * @return the built embed
     */
    public EmbedBuilder inflationStateEmbed(InflationRecord record) {
        String stateLabel = switch (record.state()) {
            case BOOM -> "\uD83D\uDE80 Boom";
            case ECONOMIC_GROWTH -> "\uD83D\uDCC8 Pertumbuhan";
            case STABLE -> "\u2696\uFE0F Stabil";
            case RECESSION -> "\uD83D\uDCC9 Resesi";
            case ECONOMIC_CRISIS -> "\uD83D\uDD25 Krisis Ekonomi";
        };

        Color color = record.state() == EconomicState.BOOM || record.state() == EconomicState.ECONOMIC_GROWTH
                ? parseColor(discordConfig.getColorPriceUp())
                : record.state() == EconomicState.STABLE
                ? parseColor(discordConfig.getColorNeutral())
                : parseColor(discordConfig.getColorCrash());

        return base()
                .setTitle("Status Ekonomi: " + stateLabel)
                .setColor(color)
                .addField("Total Uang Beredar", String.format("%.2f", record.totalMoney()), true)
                .addField("Rata-rata Saldo", String.format("%.2f", record.averageBalance()), true)
                .addField("Volume Transaksi", String.valueOf(record.tradingVolume()), true)
                .addField("Inflasi", String.format("%.2f%%", record.inflationPercent()), true)
                .addField("Deflasi", String.format("%.2f%%", record.deflationPercent()), true);
    }

    /**
     * Builds an embed listing the top rising/falling items in the market.
     *
     * @param title      embed title (e.g. "Barang Terlaris")
     * @param entryLines pre-formatted lines, one per ranked item
     * @return the built embed
     */
    public EmbedBuilder topMarketEmbed(String title, List<String> entryLines) {
        EmbedBuilder embed = base()
                .setTitle(title)
                .setColor(parseColor(discordConfig.getColorNeutral()));

        if (entryLines.isEmpty()) {
            embed.setDescription("Belum ada data.");
        } else {
            embed.setDescription(String.join("\n", entryLines));
        }
        return embed;
    }

    /**
     * Builds a generic informational embed with a title and description.
     *
     * @param title       embed title
     * @param description embed body text
     * @return the built embed
     */
    public EmbedBuilder infoEmbed(String title, String description) {
        return base()
                .setTitle(title)
                .setDescription(description)
                .setColor(parseColor(discordConfig.getColorNeutral()));
    }
}