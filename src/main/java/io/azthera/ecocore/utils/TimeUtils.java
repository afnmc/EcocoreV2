package io.azthera.ecocore.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Shared time/duration formatting helpers used by GUIs, Discord
 * embeds, and scheduler classes that need to display or reason about
 * elapsed/remaining time.
 */
public final class TimeUtils {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    private TimeUtils() {
        // Utility class, not instantiable.
    }

    /**
     * Formats an epoch-millis timestamp as a human-readable date/time
     * string in the server's local time zone.
     *
     * @param epochMillis the timestamp to format
     * @return the formatted string, e.g. "02 Aug 2026 14:30"
     */
    public static String formatTimestamp(long epochMillis) {
        return DISPLAY_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Formats a duration in ticks (20 ticks = 1 second) as a short
     * human-readable string (e.g. "3m 20s"), used for minion fuel and
     * cooldown displays.
     *
     * @param ticks the duration in Minecraft ticks
     * @return the formatted duration string
     */
    public static String formatTicksAsDuration(long ticks) {
        return formatSecondsAsDuration(ticks / 20);
    }

    /**
     * Formats a duration in seconds as a short human-readable string
     * (e.g. "1h 5m", "3m 20s", "45s").
     *
     * @param totalSeconds the duration in seconds
     * @return the formatted duration string
     */
    public static String formatSecondsAsDuration(long totalSeconds) {
        Duration duration = Duration.ofSeconds(Math.max(0, totalSeconds));
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * Whether the given timestamp falls on a different in-game "day"
     * boundary (UTC midnight) than now, used by mission reassignment
     * to decide whether daily missions need refreshing.
     *
     * @param epochMillis the timestamp to check
     * @return {@code true} if the timestamp is from a previous day
     */
    public static boolean isBeforeToday(long epochMillis) {
        Instant now = Instant.now();
        long todayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toEpochMilli();
        return epochMillis < todayStart;
    }

    /**
     * Whether the given timestamp falls in a previous ISO week
     * relative to now, used by mission reassignment to decide whether
     * weekly missions need refreshing.
     *
     * @param epochMillis the timestamp to check
     * @return {@code true} if the timestamp is from a previous week
     */
    public static boolean isBeforeThisWeek(long epochMillis) {
        long sevenDaysMillis = 7L * 24 * 60 * 60 * 1000;
        long weekStart = (System.currentTimeMillis() / sevenDaysMillis) * sevenDaysMillis;
        return epochMillis < weekStart;
    }
}