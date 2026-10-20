package com.beamremote.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WhirlpoolTest {
    @Test public void cool24AutoGoldenFrames() {
        assertArrayEquals(new byte[]{(byte)0x83,6,0,0x62,0,0,(byte)0x80,0,0,0,0,0,0,(byte)0xE2,0,2,0,0,0,0,2},
            WhirlpoolEncoder.state(false, false, 24, 0, 5, 2));
        assertArrayEquals(new byte[]{(byte)0x83,6,0,(byte)0x82,0,0,(byte)0x80,0,0,0,0,0,0,2,0,2,0,0,8,0,10},
            WhirlpoolEncoder.state(true, false, 24, 0, 5, 2));
    }
    @Test public void powerIsTransientToggleNotDesiredOnState() {
        byte[] power = WhirlpoolEncoder.state(true, true, 24, 0, 1, 17);
        assertEquals(7, power[2]); assertEquals(1, power[15]); assertEquals(9, power[20]);
        byte[] settings = WhirlpoolEncoder.state(true, false, 24, 0, 1, 17);
        assertEquals(3, settings[2]); assertEquals(17, settings[15]);
        assertEquals(0, settings[2] & 4);
    }
    @Test public void sectionsBitOrderChecksumsAndRanges() {
        for (boolean model91 : new boolean[]{false,true}) {
            int min = model91 ? 16 : 18;
            for (int temp = min; temp <= min + 14; temp++) for (int mode : new int[]{0,1,2,4})
            for (int fan : new int[]{1,2,4,5}) for (boolean toggle : new boolean[]{false,true}) {
                byte[] expected = WhirlpoolEncoder.state(model91,toggle,temp,mode,fan,6);
                Signal s = WhirlpoolEncoder.signal(model91,toggle,temp,mode,fan,6);
                int[] p = s.pattern();
                assertEquals(38000, s.frequency); assertEquals(343,p.length);
                assertEquals(8950,p[0]); assertEquals(4484,p[1]);
                assertEquals(597,p[98]); assertEquals(7920,p[99]);
                assertEquals(597,p[228]); assertEquals(7920,p[229]); assertEquals(597,p[342]);
                byte[] decoded = new byte[21]; int cursor = 2;
                for (int i = 0; i < 21; i++) {
                    for (int bit = 0; bit < 8; bit++) {
                        assertEquals(597,p[cursor++]); int space = p[cursor++];
                        assertTrue(space == 533 || space == 1649);
                        if (space == 1649) decoded[i] |= 1 << bit;
                    }
                    if (i == 5 || i == 13) cursor += 2;
                }
                assertArrayEquals(expected,decoded);
                int sum1 = 0, sum2 = 0;
                for (int i = 2; i <= 11; i++) sum1 ^= decoded[i] & 255;
                for (int i = 14; i <= 19; i++) sum2 ^= decoded[i] & 255;
                assertEquals(sum1, decoded[13] & 255); assertEquals(sum2, decoded[20] & 255);
                assertEquals(temp-min,(decoded[3] & 255) >> 4);
                assertTrue(s.durationMicros() < 2000000);
            }
        }
    }
    @Test public void rejectsUnsupportedSettings() {
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(false,false,17,0,5,2));
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(false,false,33,0,5,2));
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(true,false,15,0,5,2));
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(true,false,31,0,5,2));
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(true,false,24,5,5,2));
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(true,false,24,0,0,2));
        assertThrows(IllegalArgumentException.class, () -> WhirlpoolEncoder.state(true,false,24,0,5,1));
    }
    @Test public void profilesPreserveModelSpecificSettings() throws Exception {
        for (int choice : new int[]{6,7}) {
            Profile p = Catalog.create(choice,"Whirlpool bedroom");
            p.temperature = p.maxTemperature(); p.mode = 4; p.fan = 1; p.acCommand = 17;
            Profile copy = Profile.parse(new JSONObject(p.json().toString()));
            assertTrue(copy.isAc()); assertTrue(copy.hasAcEncoder()); assertTrue(copy.isWhirlpool());
            assertEquals(p.temperature,copy.temperature); assertEquals(17,copy.acCommand);
            assertArrayEquals(p.acSignal(false).pattern(),copy.acSignal(false).pattern());
            JSONObject invalid = p.json().put("kind","TV");
            assertThrows(IllegalArgumentException.class, () -> Profile.parse(invalid));
        }
    }
    @Test public void extraControlsHaveIndependentWireBitsAndChecksums() {
        // 24C Cool, low fan, swing + sleep. Wire fields follow the published Whirlpool layout.
        byte[] expected = {(byte)0x83,6,(byte)0x8B,0x62,0,0,(byte)0x80,0,0x40,0,0,0,0,0x29,0,3,0,0,0,0,3};
        assertArrayEquals(expected, WhirlpoolEncoder.state(false,false,24,0,5,3,true,true,false));
        byte[] turbo = WhirlpoolEncoder.state(false,false,24,0,5,4,false,false,true);
        assertEquals(1,turbo[2]); assertEquals(2,turbo[3]); assertEquals(0x90,turbo[5] & 255);
        assertEquals(0x13,turbo[13] & 255); assertEquals(4,turbo[15]);
        byte[] auto = WhirlpoolEncoder.state(false,false,24,3,1,23,false,false,false);
        assertEquals(0,auto[2]); assertEquals(0x51,auto[3]); assertEquals(0xD1,auto[13] & 255);
        assertEquals(23,auto[15]); assertEquals(23,auto[20]);
        byte[] power = WhirlpoolEncoder.state(false,true,24,0,5,4,true,false,true);
        assertEquals(0,power[5]); assertEquals(1,power[15]); assertEquals(4,power[2] & 4);
        byte[] off = WhirlpoolEncoder.state(false,false,24,0,5,7,false,false,false);
        assertEquals(0,off[2] & 0x88); assertEquals(0,off[8]);
    }
    @Test public void extraControlsPersistAndLegacyProfilesDefaultOff() throws Exception {
        Profile p = Catalog.create(6,"Bedroom");
        p.whirlpoolMode(3); assertEquals(23,p.acCommand); assertEquals(23,p.temperature); assertEquals(5,p.fan);
        p.whirlpoolSleep(true); assertEquals(0,p.mode); assertEquals(1,p.fan); assertFalse(p.turbo);
        p.swing = true;
        Profile copy = Profile.parse(new JSONObject(p.json().toString()));
        assertTrue(copy.swing); assertTrue(copy.sleep); assertFalse(copy.turbo);
        assertArrayEquals(p.acSignal(false).pattern(),copy.acSignal(false).pattern());
        p.whirlpoolTurbo(true); assertFalse(p.sleep); assertEquals(18,p.temperature); assertEquals(4,p.fan);
        p.whirlpoolMode(4); assertFalse(p.turbo);
        p.whirlpoolTurbo(true); assertEquals(32,p.temperature);
        JSONObject old = Catalog.create(6,"Legacy").json();
        old.remove("swing"); old.remove("sleep"); old.remove("turbo");
        Profile legacy = Profile.parse(old); assertFalse(legacy.swing || legacy.sleep || legacy.turbo);
        old.put("swing","true");
        assertThrows(IllegalArgumentException.class, () -> Profile.parse(old));
    }
    @Test public void advancedSignalsRemainBoundedAcrossAllFlagCombinations() {
        for (boolean m : new boolean[]{false,true}) for (boolean swing : new boolean[]{false,true})
        for (boolean sleep : new boolean[]{false,true}) for (boolean turbo : new boolean[]{false,true}) {
            byte[] b = WhirlpoolEncoder.state(m,false,24,0,5,7,swing,sleep,turbo);
            int sum = 0; for (int i=2;i<=11;i++) sum ^= b[i] & 255;
            assertEquals(sum,b[13] & 255);
            Signal signal = WhirlpoolEncoder.signal(m,false,24,0,5,7,swing,sleep,turbo);
            assertEquals(343,signal.pattern().length); assertTrue(signal.durationMicros() < 2000000);
        }
    }
    @Test public void legacyProfilesStillLoadAndDefaultCommandIsSafe() throws Exception {
        JSONObject old = Catalog.create(2,"LG AC").json(); old.remove("acCommand");
        Profile p = Profile.parse(old); assertEquals(2,p.acCommand); assertFalse(p.isWhirlpool());
        assertEquals(59,p.acSignal(true).pattern().length);
        JSONObject whirlpool = Catalog.create(6,"Whirlpool").json(); whirlpool.remove("acCommand");
        assertEquals(2,Profile.parse(whirlpool).acCommand);
    }
}
