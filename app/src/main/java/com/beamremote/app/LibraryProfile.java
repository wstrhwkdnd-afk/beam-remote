package com.beamremote.app;

import org.json.JSONObject;
import java.util.*;

/** One optional, locally imported candidate. No downloaded catalog is built into the app. */
public final class LibraryProfile {
    public final String brand, model, profileId, timing, degreeType;
    public final boolean hasTemperatureRange;
    public final int minTemperature, maxTemperature, maxFan;
    public final List<String> modes;
    public final Map<String, String> commands;
    private LibraryProfile(String brand, String model, String profileId, String timing,
                           int min, int max, int fan, String degreeType, boolean hasTemperatureRange, List<String> modes, Map<String, String> commands) {
        this.brand = brand; this.model = model; this.profileId = profileId; this.timing = timing;
        minTemperature = min; maxTemperature = max; maxFan = fan;
        this.degreeType = degreeType; this.hasTemperatureRange = hasTemperatureRange;
        this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
        this.commands = Collections.unmodifiableMap(new LinkedHashMap<>(commands));
    }
    static String text(JSONObject object, String key, int limit) {
        Object value = object.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty() || ((String) value).length() > limit)
            throw new IllegalArgumentException("Missing or invalid library field: " + key);
        return ((String) value).trim();
    }
    private static int integer(JSONObject object, String key, int low, int high) {
        Object raw = object.opt(key);
        if (raw == null || !raw.toString().matches("[0-9]{1,3}"))
            throw new IllegalArgumentException("Invalid library metadata: " + key);
        int number = Integer.parseInt(raw.toString());
        if (number < low || number > high) throw new IllegalArgumentException("Out of range metadata: " + key);
        return number;
    }
    public static LibraryProfile parse(JSONObject json) {
        String brand = text(json, "brand", 120), model = text(json, "model", 160);
        String profileId = json.has("profileId") ? text(json, "profileId", 160) : model;
        String timing = json.has("timing") ? text(json, "timing", 30) : "pronto";
        if (!timing.equals("pronto") && !timing.equals("acremote26"))
            throw new IllegalArgumentException("Unknown library timing convention.");
        JSONObject metadata = json.optJSONObject("metadata");
        if (metadata == null) metadata = new JSONObject();
        String degreeType = metadata.has("degree_type") ? text(metadata, "degree_type", 4) : "C";
        if (!degreeType.equals("C") && !degreeType.equals("F"))
            throw new IllegalArgumentException("Unknown temperature unit.");
        boolean hasRange = metadata.has("min_degree") && metadata.has("max_degree");
        int min = metadata.has("min_degree") ? integer(metadata, "min_degree", 0, 100) : (degreeType.equals("F") ? 60 : 16);
        int max = metadata.has("max_degree") ? integer(metadata, "max_degree", 0, 100) : (degreeType.equals("F") ? 86 : 30);
        int fan = metadata.has("max_fan_power") ? integer(metadata, "max_fan_power", 0, 9) : 0;
        if (min > max) throw new IllegalArgumentException("Library temperature range is reversed.");
        List<String> modes = new ArrayList<>();
        if (metadata.has("modes")) for (String item : text(metadata, "modes", 60).split(",", -1)) {
            String mode = item.trim();
            if (!Arrays.asList("C", "H", "W", "D", "A", "F", "AI").contains(mode) || modes.contains(mode))
                throw new IllegalArgumentException("Unsupported or duplicate library mode.");
            modes.add(mode);
        }
        JSONObject dictionary = json.optJSONObject("commands");
        if (dictionary == null || dictionary.length() == 0 || dictionary.length() > 2048)
            throw new IllegalArgumentException("A library profile needs 1–2,048 commands.");
        Map<String, String> commands = new LinkedHashMap<>();
        Iterator<String> keys = dictionary.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.trim().isEmpty() || key.length() > 80) throw new IllegalArgumentException("Invalid command key.");
            for (int i = 0; i < key.length(); i++) if (Character.isISOControl(key.charAt(i)))
                throw new IllegalArgumentException("Invalid command key.");
            Object raw = dictionary.opt(key);
            if (!(raw instanceof String) || ((String) raw).length() > 50000)
                throw new IllegalArgumentException("Invalid command data: " + key);
            String value = ((String) raw).trim();
            commands.put(key, value);
        }
        return new LibraryProfile(brand, model, profileId, timing, min, max, fan, degreeType, hasRange, modes, commands);
    }
    public boolean available(String key) { return commands.containsKey(key) && !commands.get(key).isEmpty(); }
    public Signal signal(String key) {
        String value = commands.get(key);
        if (value == null) throw new IllegalArgumentException("This candidate has no command for " + key + ".");
        return ProntoDecoder.decode(value, "acremote26".equals(timing));
    }
}
