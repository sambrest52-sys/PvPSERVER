package net.pvpserver.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Duration parsing and formatting helpers ("1d2h", "30m", "mm:ss").
 */
public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(mo|[smhdwy])", Pattern.CASE_INSENSITIVE);

    private TimeUtil() {
    }

    /**
     * Parses durations like {@code 1d12h}, {@code 30m}, {@code 2w}. Returns -1 when the input is invalid.
     *
     * @param input raw text
     * @return milliseconds or -1
     */
    public static long parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String text = input.toLowerCase(Locale.ROOT).trim();
        if (text.equals("perm") || text.equals("permanent") || text.equals("forever")) {
            return Long.MAX_VALUE;
        }
        Matcher matcher = PART.matcher(text);
        long total = 0;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return -1;
            }
            consumed = matcher.end();
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> amount * 1000L;
                case "m" -> amount * 60_000L;
                case "h" -> amount * 3_600_000L;
                case "d" -> amount * 86_400_000L;
                case "w" -> amount * 604_800_000L;
                case "mo" -> amount * 2_592_000_000L;
                case "y" -> amount * 31_536_000_000L;
                default -> 0L;
            };
        }
        return consumed == text.length() && total > 0 ? total : -1;
    }

    /**
     * Formats a duration as a compact human string, e.g. {@code 1d 4h 3m}.
     *
     * @param millis duration
     * @return formatted string
     */
    public static String formatDuration(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "permanent";
        }
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    /**
     * Formats a duration as {@code mm:ss} (or {@code h:mm:ss}).
     *
     * @param millis duration
     * @return clock string
     */
    public static String formatClock(long millis) {
        long total = Math.max(0, millis / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    /**
     * Formats a precise time such as a parkour run: {@code 0:07.250}, {@code 1:23.456}, {@code 1:02:03.004}.
     *
     * @param millis milliseconds
     * @return formatted time
     */
    public static String formatMillis(long millis) {
        long clamped = Math.max(0, millis);
        long hours = clamped / 3_600_000;
        long minutes = (clamped / 60_000) % 60;
        long seconds = (clamped / 1000) % 60;
        long rest = clamped % 1000;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, rest);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d.%03d", minutes, seconds, rest);
    }
}
