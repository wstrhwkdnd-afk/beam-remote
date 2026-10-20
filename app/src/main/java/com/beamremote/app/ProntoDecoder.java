package com.beamremote.app;

/** Decoder for learned, modulated Pronto (0000); preserves both declared sequences. */
public final class ProntoDecoder {
    private ProntoDecoder() {}
    private static int[] words(String text, boolean acremote26) {
        if (text == null || text.length() > 50000 || text.trim().isEmpty())
            throw new IllegalArgumentException("Pronto text is empty or too large.");
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 8 || parts.length > 4100)
            throw new IllegalArgumentException("Invalid Pronto sequence length.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("[0-9a-fA-F]{4}"))
                throw new IllegalArgumentException("Pronto words must contain four hexadecimal digits.");
            values[i] = Integer.parseInt(parts[i], 16);
        }
        if (values[0] != 0 || values[1] == 0)
            throw new IllegalArgumentException("Only modulated learned Pronto (0000) is supported.");
        long count = 2L * (values[2] + values[3]);
        if ((!acremote26 && (count < 4 || count > 4096 || count + 4 != values.length))
                || (values.length - 4) % 2 != 0)
            throw new IllegalArgumentException("Pronto header does not match its declared sequence length.");
        for (int i = 4; i < values.length; i++) if (values[i] == 0)
            throw new IllegalArgumentException("Pronto durations must be positive.");
        return values;
    }
    public static void validate(String text) { words(text, false); }
    public static Signal decode(String text) { return decode(text, false); }
    public static Signal decode(String text, boolean acremote26) {
        int[] values = words(text, acremote26);
        double period = values[1] * 0.241246;
        int frequency = acremote26 ? (int) (1000000.0 / period) : (int) Math.round(1000000.0 / period);
        int[] pattern = new int[values.length - 4];
        for (int i = 0; i < pattern.length; i++) {
            long duration = Math.round(values[i + 4] * (acremote26 ? 26.0 : period));
            if (duration <= 0 || duration > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Pronto duration is out of range.");
            pattern[i] = (int) duration;
        }
        return new Signal(frequency, pattern);
    }
}
