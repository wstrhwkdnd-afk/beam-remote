package com.beamremote.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileTest {
    @Test public void allPresetsRoundTrip() throws Exception {
        for (int i = 0; i < Catalog.OPTIONS.length; i++) {
            Profile original = Catalog.create(i, "Bedroom");
            Profile restored = Profile.parse(new JSONObject(original.json().toString()));
            assertEquals(original.id, restored.id); assertEquals(original.protocol, restored.protocol);
            assertEquals(original.commands.keySet(), restored.commands.keySet());
            for (String key : original.commands.keySet()) assertArrayEquals(original.commands.get(key).pattern(), restored.commands.get(key).pattern());
        }
    }
    @Test public void customAcSceneSurvivesExport() throws Exception {
        Profile p = Catalog.create(5, "Study AC"); p.commands.put("Cool 24 auto", Encoders.lg(false, true, 24, 0, 5));
        assertEquals(1, Profile.parse(p.json()).commands.size());
    }
    @Test public void rejectsUnknownVersionsAndInvalidTypes() throws Exception {
        JSONObject version = Catalog.create(0, "TV").json().put("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> Profile.parse(version));
        JSONObject type = Catalog.create(0, "TV").json().put("protocol", "LG28");
        assertThrows(IllegalArgumentException.class, () -> Profile.parse(type));
        JSONObject fraction = Catalog.create(0, "TV").json().put("temperature", 24.5);
        assertThrows(IllegalArgumentException.class, () -> Profile.parse(fraction));
        JSONObject blank = Catalog.create(0, "TV").json().put("name", "  ");
        assertThrows(IllegalArgumentException.class, () -> Profile.parse(blank));
    }
    @Test public void rejectsMalformedRawImport() throws Exception {
        JSONObject profile = Catalog.create(4, "Custom").json();
        profile.getJSONObject("commands").put("Power", new JSONObject().put("frequency", 38000)
            .put("pattern", new JSONArray("[9000,-4500,560]")));
        assertThrows(IllegalArgumentException.class, () -> Profile.parse(profile));
    }
}
