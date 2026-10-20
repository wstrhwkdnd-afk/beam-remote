package com.beamremote.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

public final class ProfileStore {
    private final SharedPreferences prefs;
    public ProfileStore(Context context) { prefs = context.getSharedPreferences("profiles-v1", Context.MODE_PRIVATE); }
    public List<Profile> load() throws JSONException {
        JSONArray array = new JSONArray(prefs.getString("profiles", "[]"));
        List<Profile> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) result.add(Profile.parse(array.getJSONObject(i)));
        return result;
    }
    public void save(List<Profile> profiles) {
        try {
            JSONArray array = new JSONArray();
            for (Profile profile : profiles) array.put(profile.json());
            if (!prefs.edit().putString("profiles", array.toString()).commit())
                throw new IllegalStateException("Could not save profiles. Check available storage.");
        } catch (JSONException e) { throw new IllegalStateException("Could not serialize profiles.", e); }
    }
    public String selected() { return prefs.getString("selected", ""); }
    public void select(String id) { prefs.edit().putString("selected", id).apply(); }
}
