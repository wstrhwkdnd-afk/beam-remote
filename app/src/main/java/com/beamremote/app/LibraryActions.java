package com.beamremote.app;

import java.util.*;
import java.util.regex.*;

/** Resolves only commands actually present in a candidate; never guesses an IR payload. */
public final class LibraryActions {
    private LibraryActions() {}
    public static String first(LibraryProfile p, String... keys) {
        for (String key : keys) if (p.available(key)) return key;
        return null;
    }
    public static String state(LibraryProfile p, String mode, int temperature, String fan) {
        if (mode.equals("A") || mode.equals("AI")) return first(p, "AI0", "AI", "A");
        return first(p, mode + temperature + "F" + fan, mode + "F" + fan);
    }
    public static String temperature(LibraryProfile p, Profile saved, int delta) {
        int next = saved.temperature + delta;
        if (next < 0 || next > 100) return null;
        if (p.hasTemperatureRange && (next < p.minTemperature || next > p.maxTemperature)) return null;
        String relative = delta > 0 ? first(p,"UP","TEMP_UP","PLUS","HIGH","HI","UPE","KEY_TEMP_UP","BTN_TEMP_UP") : first(p,"DOWN","TEMP_DOWN","MINUS","LOW","LO","DOWNE","KEY_TEMP_DOWN","BTN_TEMP_DOWN");
        return relative != null ? relative : first(p,saved.libraryModeCode + next + "F" + saved.libraryFanCode);
    }
    public static List<String> modes(LibraryProfile p) {
        LinkedHashSet<String> modes = new LinkedHashSet<>(p.modes);
        for (String key : p.commands.keySet()) {
            Matcher m = Pattern.compile("^([CHWD])(?:[0-9]+)?F[0-9A]$").matcher(key);
            if (m.matches()) modes.add(m.group(1));
        }
        if (first(p,"AI0","AI","A") != null) modes.add("A");
        return new ArrayList<>(modes);
    }
    public static List<String> fans(LibraryProfile p) {
        TreeSet<String> values = new TreeSet<>();
        for (String key : p.commands.keySet()) {
            Matcher m = Pattern.compile("^[A-Z]+(?:[0-9]+)?F([0-9A])$").matcher(key);
            if (m.matches()) values.add(m.group(1));
        }
        return new ArrayList<>(values);
    }
    public static String next(List<String> values, String current) {
        if (values.isEmpty()) return current;
        int i = values.indexOf(current); return values.get((i + 1) % values.size());
    }
    public static String modeName(String code) {
        switch (code) { case "C": return "Cool"; case "H": return "Heat"; case "W": case "F": return "Fan";
            case "D": return "Dry"; case "A": case "AI": return "Auto"; default: return code; }
    }
    public static void initialize(Profile saved, LibraryProfile source) {
        List<String> modes = modes(source), fans = fans(source);
        if (!modes.isEmpty()) saved.libraryModeCode = modes.get(0);
        if (!fans.isEmpty()) saved.libraryFanCode = fans.get(0);
        saved.temperature = Math.max(source.minTemperature, Math.min(source.maxTemperature, 24));
        int distance = Integer.MAX_VALUE;
        for (String key : source.commands.keySet()) {
            Matcher m = Pattern.compile("^([CHWD])([0-9]{1,3})F([0-9A])$").matcher(key);
            if (!m.matches() || !source.available(key)) continue;
            int temp = Integer.parseInt(m.group(2));
            if (temp > 100) continue;
            int score = Math.abs(temp - 24) * 10 + (m.group(1).equals("C") ? 0 : 5);
            if (score < distance) { distance = score; saved.temperature = temp; saved.libraryModeCode = m.group(1); saved.libraryFanCode = m.group(3); }
        }
    }
}
