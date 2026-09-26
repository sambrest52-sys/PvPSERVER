package net.pvpserver.lobby.layout;

import java.util.Locale;

/**
 * Parsing and formatting of the space separated numbers used throughout layout.yml.
 */
final class LayoutNumbers {

    private LayoutNumbers() {
    }

    static double[] parse(String text, int min, int max) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("missing position");
        }
        String[] parts = text.trim().split("[\\s,]+");
        if (parts.length < min || parts.length > max) {
            throw new IllegalArgumentException("expected " + (min == max ? min : min + "-" + max) + " numbers but got \"" + text + "\"");
        }
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + parts[i] + "\" is not a number in \"" + text + "\"");
            }
            if (!Double.isFinite(values[i])) {
                throw new IllegalArgumentException("\"" + parts[i] + "\" is not a finite number");
            }
        }
        return values;
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return Long.toString((long) value);
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
