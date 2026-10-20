package com.beamremote.app;

/** Independently implemented encoders; wire-format references are in docs/PROTOCOLS.md. */
public final class Encoders {
    private Encoders() {}
    public static Signal samsung(int command) {
        if (command < 0 || command > 255) throw new IllegalArgumentException("Invalid Samsung command.");
        long value = 0x0707L | ((long) command << 16) | ((long) (command ^ 255) << 24);
        return pulse(value, 32, false, 4500, 4500, 560, 1690, 560);
    }
    public static Signal nec(int address, int command) {
        if (address < 0 || address > 255 || command < 0 || command > 255)
            throw new IllegalArgumentException("NEC address and command must be 0–255.");
        long value = address | ((long) (address ^ 255) << 8) | ((long) command << 16) | ((long) (command ^ 255) << 24);
        return pulse(value, 32, false, 9000, 4500, 560, 1690, 560);
    }
    public static long lgCode(boolean power, int temperature, int mode, int fan) {
        if (temperature < 16 || temperature > 30 || mode < 0 || mode > 4 ||
                !(fan == 0 || fan == 1 || fan == 2 || fan == 4 || fan == 5))
            throw new IllegalArgumentException("Unsupported LG AC setting.");
        if (!power) return 0x88C0051L;
        int command = (mode << 8) | ((temperature - 15) << 4) | fan;
        int checksum = 0;
        for (int shift = 0; shift < 16; shift += 4) checksum += (command >> shift) & 15;
        return 0x8800000L | ((long) command << 4) | (checksum & 15);
    }
    public static Signal lg(boolean variant2, boolean power, int temperature, int mode, int fan) {
        return pulse(lgCode(power, temperature, mode, fan), 28, true,
                variant2 ? 3200 : 8500, variant2 ? 9900 : 4250, variant2 ? 480 : 550, 1600, 550);
    }
    private static Signal pulse(long value, int bits, boolean msb, int headerMark, int headerSpace,
                                int mark, int one, int zero) {
        int[] result = new int[bits * 2 + 3];
        result[0] = headerMark; result[1] = headerSpace;
        for (int i = 0; i < bits; i++) {
            result[2 + i * 2] = mark;
            result[3 + i * 2] = ((value >>> (msb ? bits - i - 1 : i)) & 1) == 1 ? one : zero;
        }
        result[result.length - 1] = mark;
        return new Signal(38000, result);
    }
}
