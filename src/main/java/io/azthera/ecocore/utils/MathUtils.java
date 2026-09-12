package io.azthera.ecocore.utils;

/**
 * Small numeric helpers shared across the AI engine, inflation
 * engine, and GUI formatting code.
 */
public final class MathUtils {

    private MathUtils() {
        // Utility class, not instantiable.
    }

    /**
     * Clamps a value into the inclusive range [min, max].
     *
     * @param value the value to clamp
     * @param min   the minimum allowed value
     * @param max   the maximum allowed value
     * @return the clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a value into the inclusive integer range [min, max].
     *
     * @param value the value to clamp
     * @param min   the minimum allowed value
     * @param max   the maximum allowed value
     * @return the clamped value
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linearly interpolates between two values.
     *
     * @param from     the starting value
     * @param to       the ending value
     * @param fraction the interpolation fraction, typically 0.0-1.0
     * @return the interpolated value
     */
    public static double lerp(double from, double to, double fraction) {
        return from + ((to - from) * fraction);
    }

    /**
     * Computes what percentage {@code part} is of {@code whole}, safely
     * handling a zero {@code whole} by returning 0 instead of dividing by zero.
     *
     * @param part  the partial amount
     * @param whole the total amount
     * @return the percentage, 0-100 under normal inputs
     */
    public static double percentOf(double part, double whole) {
        if (whole == 0) {
            return 0.0;
        }
        return (part / whole) * 100.0;
    }

    /**
     * Rounds a value to a given number of decimal places.
     *
     * @param value  the value to round
     * @param places the number of decimal places, must be non-negative
     * @return the rounded value
     */
    public static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}