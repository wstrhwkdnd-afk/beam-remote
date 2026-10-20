package com.beamremote.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {
    private long decode(Signal signal, boolean msb) {
        int[] p = signal.pattern(); long result = 0; int bits = (p.length - 3) / 2;
        for (int i = 0; i < bits; i++) if (p[i * 2 + 3] > 1000) result |= 1L << (msb ? bits - i - 1 : i);
        return result;
    }
    @Test public void samsungPowerHasCorrectWireOrder() {
        Signal signal = Encoders.samsung(2);
        assertEquals(38000, signal.frequency); assertEquals(67, signal.pattern().length);
        assertEquals(4500, signal.pattern()[0]); assertEquals(4500, signal.pattern()[1]);
        // Legacy MSB wire representation of Samsung power is E0E040BF.
        assertEquals(0xE0E040BFL, decode(signal, true));
    }
    @Test public void lgTvUsesStandardNecComplementBytes() {
        Signal signal = Encoders.nec(4, 8);
        assertEquals(0x20DF10EFL, decode(signal, true));
        assertEquals(9000, signal.pattern()[0]);
    }
    @Test public void acGoldenFramesAndVariants() {
        assertEquals(0x880095EL, Encoders.lgCode(true, 24, 0, 5));
        assertEquals(0x88C0051L, Encoders.lgCode(false, 24, 0, 5));
        assertEquals(0x8804952L, Encoders.lgCode(true, 24, 4, 5));
    }
    @Test public void acSettingsHaveValidChecksumsAndFields() {
        for (int temp = 16; temp <= 30; temp++) for (int mode = 0; mode <= 4; mode++) for (int fan : new int[]{0,1,2,4,5}) {
            long code = Encoders.lgCode(true, temp, mode, fan);
            assertEquals(temp - 15, (code >> 8) & 15); assertEquals(mode, (code >> 12) & 7); assertEquals(fan, (code >> 4) & 15);
            int sum = 0; for (int shift = 4; shift <= 16; shift += 4) sum += (code >> shift) & 15;
            assertEquals(sum & 15, code & 15);
            for (boolean variant : new boolean[]{false, true}) {
                Signal signal = Encoders.lg(variant, true, temp, mode, fan);
                assertEquals(code, decode(signal, true)); assertEquals(59, signal.pattern().length);
                assertEquals(variant ? 3200 : 8500, signal.pattern()[0]);
                assertEquals(variant ? 9900 : 4250, signal.pattern()[1]);
                assertEquals(variant ? 480 : 550, signal.pattern()[2]);
                assertTrue(signal.durationMicros() < 2000000);
            }
        }
    }
    @Test public void rejectsBadSignalsAndProtectsArrays() {
        assertThrows(IllegalArgumentException.class, () -> new Signal(0, new int[]{1,2,3}));
        assertThrows(IllegalArgumentException.class, () -> new Signal(38000, new int[]{1,0,3}));
        assertThrows(IllegalArgumentException.class, () -> new Signal(38000, new int[]{1000000,999999,1}));
        assertThrows(IllegalArgumentException.class, () -> new Signal(38000, new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE,1}));
        assertThrows(IllegalArgumentException.class, () -> Signal.parse(38000, "9000, nope, 560"));
        assertThrows(IllegalArgumentException.class, () -> Encoders.lgCode(true, 31, 0, 5));
        int[] raw = {9000,4500,560}; Signal signal = new Signal(38000, raw); raw[0] = 1;
        int[] copy = signal.pattern(); copy[0] = 1; assertEquals(9000, signal.pattern()[0]);
        assertArrayEquals(new int[]{9000,4500,560}, Signal.parse(38000, "9000, 4500\n560").pattern());
    }
}
