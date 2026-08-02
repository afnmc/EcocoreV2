package io.azthera.ecocore.gui.graph;

import io.azthera.ecocore.model.MarketSnapshot;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Renders an item's price history over a chosen period ("24h", "7d",
 * "30d", "90d") into a PNG chart image, using JFreeChart. Used by
 * {@code /prices} chat output links, the shop's price-graph feature,
 * and Discord market embeds ({@code DiscordEmbedBuilder} attaches the
 * PNG bytes this class produces).
 */
public final class PriceGraphRenderer {

    private static final int IMAGE_WIDTH = 800;
    private static final int IMAGE_HEIGHT = 450;

    private final Logger logger;

    /**
     * Creates a price graph renderer.
     *
     * @param logger plugin logger for rendering failures
     */
    public PriceGraphRenderer(Logger logger) {
        this.logger = logger;
    }

    /**
     * Resolves how far back a named period spans, in milliseconds.
     *
     * @param period one of "24h", "7d", "30d", "90d"
     * @return the window in milliseconds, defaulting to 24h for unrecognized values
     */
    public long resolvePeriodMillis(String period) {
        return switch (period) {
            case "7d" -> 7L * 24 * 60 * 60 * 1000;
            case "30d" -> 30L * 24 * 60 * 60 * 1000;
            case "90d" -> 90L * 24 * 60 * 60 * 1000;
            default -> 24L * 60 * 60 * 1000;
        };
    }

    /**
     * Renders a price-over-time line chart for the given snapshots.
     *
     * @param itemId    the item id, used for the chart title
     * @param period    the period label being displayed (e.g. "7d"), used in the title
     * @param snapshots the snapshots to plot, expected oldest-first
     * @return the rendered PNG image bytes, or {@code null} if rendering failed
     */
    public byte[] renderPriceChart(String itemId, String period, List<MarketSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return null;
        }

        TimeSeries series = new TimeSeries("Harga");
        for (MarketSnapshot snapshot : snapshots) {
            series.addOrUpdate(new Millisecond(new Date(snapshot.timestamp())), snapshot.price());
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                itemId + " - " + period,
                "Waktu",
                "Harga",
                dataset,
                false,
                true,
                false
        );

        try {
            BufferedImage image = chart.createBufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            logger.severe("[EcoCore] Failed to render price chart for " + itemId + ": " + exception.getMessage());
            return null;
        }
    }
}