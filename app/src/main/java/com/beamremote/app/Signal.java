package com.beamremote.app;

/** Immutable, bounded ConsumerIrManager input. Durations alternate mark/space in µs. */
public final class Signal {
    public final int frequency;
    private final int[] durations;
    public Signal(int frequency, int[] pattern) {
        if (frequency < 1000 || frequency > 1000000) throw new IllegalArgumentException("Carrier must be 1,000–1,000,000 Hz.");
        if (pattern == null || pattern.length < 3 || pattern.length > 4096)
            throw new IllegalArgumentException("Enter 3–4,096 alternating mark/space durations.");
        long total = 0;
        for (int duration : pattern) {
            if (duration <= 0) throw new IllegalArgumentException("Every duration must be a positive integer in microseconds.");
            total += duration;
        }
        if (total >= 2000000) throw new IllegalArgumentException("A signal must be shorter than 2 seconds.");
        this.frequency = frequency;
        this.durations = pattern.clone();
    }
    public int[] pattern() { return durations.clone(); }
    public long durationMicros() { long sum = 0; for (int d : durations) sum += d; return sum; }
    public static Signal parse(int frequency, String raw) {
        if (raw.length() > 50000) throw new IllegalArgumentException("Signal text is too large.");
        String[] parts = raw.trim().split("[\\s,]+");
        int[] values = new int[parts.length];
        try { for (int i = 0; i < parts.length; i++) values[i] = Integer.parseInt(parts[i]); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Use decimal microseconds, separated by commas or spaces."); }
        return new Signal(frequency, values);
    }
}
