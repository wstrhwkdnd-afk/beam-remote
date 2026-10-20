package com.beamremote.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryActionsTest {
    private LibraryProfile source() throws Exception {
        JSONObject commands = new JSONObject();
        for (String key : new String[]{"C24F1","C25F1","C24FA","H24F1","WF1","WF2","POWER_ON","POWER_OFF","AI"})
            commands.put(key,"0000 006D 0002 0000 0010 0010 0010 0010");
        return LibraryProfile.parse(new JSONObject().put("brand","Example").put("model","Example1")
            .put("metadata",new JSONObject().put("modes","C,H,W,A").put("min_degree","16").put("max_degree","30"))
            .put("commands",commands));
    }
    @Test public void oneDegreeSelectsExactFrameAndNeverReusesFanOnlyFrame() throws Exception {
        LibraryProfile source = source(); Profile p = new Profile(); p.temperature=24;
        assertEquals("C25F1",LibraryActions.temperature(source,p,1));
        assertNull(LibraryActions.temperature(source,p,-1));
        p.libraryModeCode="W";
        assertNull(LibraryActions.temperature(source,p,1));
        assertEquals("WF1",LibraryActions.state(source,"W",24,"1"));
        assertNull(LibraryActions.state(source,"C",26,"1"));
    }
    @Test public void sourceModesFansAndAutoAreAvailableWithoutInventingCommands() throws Exception {
        LibraryProfile p=source();
        assertTrue(LibraryActions.modes(p).contains("W")); assertTrue(LibraryActions.fans(p).contains("A"));
        assertEquals("AI",LibraryActions.state(p,"A",24,"1"));
        assertEquals("H",LibraryActions.next(LibraryActions.modes(p),"C"));
        assertEquals("POWER_ON",LibraryActions.first(p,"ON","POWER_ON"));
    }
    @Test public void libraryReferencesRoundTripWithoutBeingTreatedAsLgSettings() throws Exception {
        Profile p=new Profile();p.kind="AC";p.protocol="LIBRARY";p.name="Room";p.brand="Example";p.model="Example1";
        p.temperature=64;p.libraryModeCode="C";p.libraryFanCode="A";
        p.libraryId="a".repeat(64);p.libraryEntry="profiles/example/example1.json";
        Profile copy=Profile.parse(new JSONObject(p.json().toString()));
        assertTrue(copy.isLibrary());assertEquals(64,copy.temperature);assertEquals("A",copy.libraryFanCode);
        assertEquals(p.libraryEntry,copy.libraryEntry);
        assertThrows(IllegalArgumentException.class,()->copy.acSignal(false));
        JSONObject bad=p.json().put("libraryEntry","profiles/../other.json");
        assertThrows(IllegalArgumentException.class,()->Profile.parse(bad));
        final JSONObject wrongKind=p.json().put("kind","TV");
        assertThrows(IllegalArgumentException.class,()->Profile.parse(wrongKind));
    }
}
