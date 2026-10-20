package com.beamremote.app;

/** Whirlpool 21-byte frames. Protocol research and supported scope: docs/PROTOCOLS.md. */
public final class WhirlpoolEncoder {
    private WhirlpoolEncoder() {}
    public static byte[] state(boolean model91, boolean togglePower, int temperature, int mode, int fan, int command) {
        return state(model91, togglePower, temperature, mode, fan, command, false, false, false);
    }
    public static byte[] state(boolean model91, boolean togglePower, int temperature, int mode, int fan, int command,
                               boolean swing, boolean sleep, boolean turbo) {
        int min = model91 ? 16 : 18;
        if (temperature < min || temperature > min + 14 ||
            !(mode == 0 || mode == 1 || mode == 2 || mode == 3 || mode == 4) ||
            !(fan == 1 || fan == 2 || fan == 4 || fan == 5) ||
            !(command == 2 || command == 3 || command == 4 || command == 6 || command == 7 || command == 17 || command == 23))
            throw new IllegalArgumentException("Unsupported Whirlpool temperature, mode, fan or command.");
        if (mode == 3) { temperature = 23; fan = 5; sleep = false; turbo = false; }
        if (turbo && !togglePower) {
            if (mode != 4) mode = 0;
            temperature = mode == 4 ? min + 14 : min;
            fan = 4;
        }
        if (sleep && !turbo) fan = 1;
        // Public profile values remain the same across brands; translate to Whirlpool wire values.
        int wireMode = mode == 0 ? 2 : mode == 1 ? 3 : mode == 2 ? 4 : mode == 3 ? 1 : 0;
        int wireFan = fan == 5 ? 0 : fan == 4 ? 1 : fan == 2 ? 2 : 3;
        byte[] bytes = new byte[21];
        bytes[0] = (byte) 0x83; bytes[1] = 6;
        bytes[2] = (byte) (wireFan | (togglePower ? 4 : 0) | (sleep ? 8 : 0) | (swing ? 128 : 0));
        bytes[3] = (byte) (((temperature - min) << 4) | wireMode);
        bytes[5] = (byte) (turbo && !togglePower ? 0x90 : 0);
        bytes[6] = (byte) 0x80;
        bytes[8] = (byte) (swing ? 0x40 : 0);
        bytes[15] = (byte) (togglePower ? 1 : command);
        bytes[18] = (byte) (model91 ? 8 : 0);
        // Byte 12 is reserved and is not part of checksum 1.
        for (int i = 2; i <= 11; i++) bytes[13] ^= bytes[i];
        for (int i = 14; i <= 19; i++) bytes[20] ^= bytes[i];
        return bytes;
    }
    public static Signal signal(boolean model91, boolean togglePower, int temperature, int mode, int fan, int command) {
        return signal(model91, togglePower, temperature, mode, fan, command, false, false, false);
    }
    public static Signal signal(boolean model91, boolean togglePower, int temperature, int mode, int fan, int command,
                                boolean swing, boolean sleep, boolean turbo) {
        byte[] bytes = state(model91, togglePower, temperature, mode, fan, command, swing, sleep, turbo);
        int[] pattern = new int[343]; int cursor = 0;
        pattern[cursor++] = 8950; pattern[cursor++] = 4484;
        for (int i = 0; i < bytes.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                pattern[cursor++] = 597;
                pattern[cursor++] = ((bytes[i] >>> bit) & 1) == 0 ? 533 : 1649;
            }
            if (i == 5 || i == 13) { pattern[cursor++] = 597; pattern[cursor++] = 7920; }
        }
        pattern[cursor] = 597;
        return new Signal(38000, pattern);
    }
}
