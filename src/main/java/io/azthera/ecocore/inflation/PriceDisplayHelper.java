package io.azthera.ecocore.inflation;

import io.azthera.ecocore.model.InflationRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the simple, player-facing inflation price display formula
 * from Revisi 16: {@code buy = base * (1 + inflationPercent / 100)}
 * and {@code sell = base * (1 - inflationPercent / 100)} (deflation
 * mirrors both directions). This is a presentational overlay only -
 * it never feeds back into {@code AiEconomyEngine}'s actual
 * AI-computed transaction prices, which remain the source of truth
 * for what a player is actually charged/paid.
 */
public final class PriceDisplayHelper {

    private PriceDisplayHelper() {
    }

    public record DisplayPrices(double buyPrice, double sellPrice, double percentChange, boolean isInflation) {
    }

    public static DisplayPrices resolve(double basePrice, InflationRecord record) {
        if (record == null) {
            return new DisplayPrices(basePrice, basePrice, 0.0, true);
        }
        double signedPercent = record.inflationPercent() > 0
                ? record.inflationPercent()
                : -record.deflationPercent();
        double buyPrice = basePrice * (1 + (signedPercent / 100.0));
        double sellPrice = basePrice * (1 - (signedPercent / 100.0));
        buyPrice = Math.max(0.01, buyPrice);
        sellPrice = Math.max(0.01, sellPrice);
        return new DisplayPrices(buyPrice, sellPrice, signedPercent, signedPercent >= 0);
    }

    public static List<String> buildPriceLoreLines(double basePrice, DisplayPrices display) {
        if (Math.abs(display.percentChange()) < 0.01) {
            return List.of();
        }
        String arrowLine = "\u00a77Harga: \u00a7f" + String.format("%.2f", basePrice)
                + " \u00a77> \u00a7f" + String.format("%.2f", display.buyPrice());
        String reasonLine = display.isInflation()
                ? "\u00a77Perubahan karena inflasi \u00a7c" + String.format("%.1f", Math.abs(display.percentChange())) + "%"
                : "\u00a77Perubahan karena deflasi \u00a7a" + String.format("%.1f", Math.abs(display.percentChange())) + "%";
        return List.of(arrowLine, reasonLine);
    }

    public static List<String> buildEconomySummaryLines(InflationRecord record) {
        if (record == null) {
            return List.of("\u00a77Data ekonomi belum tersedia.");
        }
        boolean isInflation = record.inflationPercent() >= record.deflationPercent();
        double percent = isInflation ? record.inflationPercent() : record.deflationPercent();
        if (percent < 0.01) {
            return List.of("\u00a77Ekonomi server stabil.");
        }
        List<String> lines = new ArrayList<>();
        if (isInflation) {
            lines.add("\u00a76Server inflasi");
            lines.add("\u00a7c\u25b5 " + String.format("%.1f", percent) + "%");
            lines.add("\u00a77Harga per barang naik " + String.format("%.1f", percent) + "% dari sebelumnya");
            lines.add("\u00a77Harga jual turun " + String.format("%.1f", percent) + "% dari sebelumnya");
        } else {
            lines.add("\u00a7bServer deflasi");
            lines.add("\u00a7a\u25be " + String.format("%.1f", percent) + "%");
            lines.add("\u00a77Harga per barang turun " + String.format("%.1f", percent) + "% dari sebelumnya");
            lines.add("\u00a77Harga jual naik " + String.format("%.1f", percent) + "% dari sebelumnya");
        }
        return lines;
    }
}