// FILE: src/main/java/io/azthera/ecocore/inflation/PriceDisplayHelper.java
package io.azthera.ecocore.inflation;

import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.model.ShopItemRecord;

/**
 * Computes the simple, player-facing inflation price display formula
 * from Revisi 16: {@code buy = base * (1 + inflationPercent / 100)}
 * and {@code sell = base * (1 - inflationPercent / 100)} (deflation
 * mirrors both directions). This is a presentational overlay only -
 * it never feeds back into {@code AiEconomyEngine}'s actual
 * AI-computed transaction prices, which remain the source of truth
 * for what a player is actually charged/paid. This helper exists so
 * shop/market/sell lore can show a consistent "why did the price
 * change" explanation without touching the learning-model pricing
 * pipeline.
 */
public final class PriceDisplayHelper {

    private PriceDisplayHelper() {
    }

    /**
     * A resolved display price pair for one item under the current
     * economic record.
     *
     * @param buyPrice the display buy price
     * @param sellPrice the display sell price
     * @param percentChange the signed percent change from base (positive = inflation, negative = deflation)
     * @param isInflation {@code true} if the economy is currently inflating rather than deflating
     */
    public record DisplayPrices(double buyPrice, double sellPrice, double percentChange, boolean isInflation) {
    }

    /**
     * Computes the simple display buy/sell price pair for an item
     * from its base price and the current inflation record. Rounding
     * is left to the caller (typically via {@code PricesConfig}'s
     * configured rounding mode/step) since this helper only computes
     * the raw formula result.
     *
     * @param basePrice the item's configured reference base price
     * @param record the current inflation record, may be {@code null} if no cycle has run yet
     * @return the resolved display prices, unchanged from base if {@code record} is {@code null}
     */
    public static DisplayPrices resolve(double basePrice, InflationRecord record) {
        if (record == null) {
            return new DisplayPrices(basePrice, basePrice, 0.0, true);
        }
        double signedPercent = record.inflationPercent() > 0
                ? record.inflationPercent()
                : -record.deflationPercent();
        // Revisi 16 formula, applied symmetrically for inflation (positive)
        // and deflation (negative signedPercent already flips the sign
        // correctly for both branches below).
        double buyPrice = basePrice * (1 + (signedPercent / 100.0));
        double sellPrice = basePrice * (1 - (signedPercent / 100.0));
        // Revisi 16: never produce a negative price regardless of how
        // extreme the deflation/inflation swing is.
        buyPrice = Math.max(0.01, buyPrice);
        sellPrice = Math.max(0.01, sellPrice);
        return new DisplayPrices(buyPrice, sellPrice, signedPercent, signedPercent >= 0);
    }

    /**
     * Builds the short lore explanation lines shown under an item's
     * price (Revisi 16): "Harga: base > new" then the reason line.
     *
     * @param basePrice the item's base price
     * @param display the resolved display prices from {@link #resolve}
     * @return two lore lines ready to append to an item's lore list
     */
    public static java.util.ListString> buildPriceLoreLines(double basePrice, DisplayPrices display) {
        if (Math.abs(display.percentChange()) 0.01) {
            return java.util.List.of();
        }
        String arrowLine = "§7Harga: §f" + String.format("%.2f", basePrice)
                + " §7> §f" + String.format("%.2f", display.buyPrice());
        String reasonLine = display.isInflation()
                ? "§7Perubahan karena inflasi §c" + String.format("%.1f", Math.abs(display.percentChange())) + "%"
                : "§7Perubahan karena deflasi §a" + String.format("%.1f", Math.abs(display.percentChange())) + "%";
        return java.util.List.of(arrowLine, reasonLine);
    }

    /**
     * Builds the summary block shown in the server-wide inflation/
     * deflation banner (Revisi 16), e.g. for {@code /inflation} or a
     * broadcast notification.
     *
     * @param record the current inflation record
     * @return the formatted summary lines
     */
    public static java.util.ListString> buildEconomySummaryLines(InflationRecord record) {
        if (record == null) {
            return java.util.List.of("§7Data ekonomi belum tersedia.");
        }
        boolean isInflation = record.inflationPercent() >= record.deflationPercent();
        double percent = isInflation ? record.inflationPercent() : record.deflationPercent();
        if (percent 0.01) {
            return java.util.List.of("§7Ekonomi server stabil.");
        }
        java.util.ListString> lines = new java.util.ArrayList<>();
        if (isInflation) {
            lines.add("§6Server inflasi");
            lines.add("§c▵ " + String.format("%.1f", percent) + "%");
            lines.add("§7Harga per barang naik " + String.format("%.1f", percent) + "% dari sebelumnya");
            lines.add("§7Harga jual turun " + String.format("%.1f", percent) + "% dari sebelumnya");
        } else {
            lines.add("§bServer deflasi");
            lines.add("§a▾ " + String.format("%.1f", percent) + "%");
            lines.add("§7Harga per barang turun " + String.format("%.1f", percent) + "% dari sebelumnya");
            lines.add("§7Harga jual naik " + String.format("%.1f", percent) + "% dari sebelumnya");
        }
        return lines;
    }
}