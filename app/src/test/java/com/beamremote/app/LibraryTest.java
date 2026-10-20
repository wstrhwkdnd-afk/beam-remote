package com.beamremote.app;

import org.json.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class LibraryTest {
    private static final String PRONTO = "0000 006D 0001 0001 0010 0020 0030 0040";
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private JSONObject profile() throws Exception {
        return new JSONObject().put("brand", "Example").put("model", "candidate1")
            .put("metadata", new JSONObject().put("degree_type", "C").put("min_degree", "16")
                .put("max_degree", "30").put("max_fan_power", "3").put("modes", "C,H,W,D,A"))
            .put("commands", new JSONObject().put("POWER_ON", PRONTO).put("C24F1", PRONTO));
    }
    private JSONObject index(int count) throws Exception {
        JSONArray profiles = new JSONArray();
        for (int i = 0; i < count; i++) profiles.put(new JSONObject().put("id", "candidate" + i)
            .put("name", "Candidate " + i).put("entry", "profiles/example/candidate" + i + ".json"));
        return new JSONObject().put("format", "beam-profile-library").put("version", 1).put("source", "Synthetic tests")
            .put("brands", new JSONArray().put(new JSONObject().put("id", "example").put("name", "Example").put("profiles", profiles)));
    }
    private File zip(Map<String, String> entries) throws Exception {
        File file = temp.newFile();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, String> item : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(item.getKey()));
                output.write(item.getValue().getBytes(StandardCharsets.UTF_8)); output.closeEntry();
            }
        }
        return file;
    }
    private Map<String, String> contents(int count) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>(); entries.put("index.json", index(count).toString());
        for (int i = 0; i < count; i++) entries.put("profiles/example/candidate" + i + ".json", profile().toString());
        return entries;
    }
    @Test public void standardProntoUsesFrequencyPeriodAndIncludesRepeatSequence() {
        Signal decoded = ProntoDecoder.decode(PRONTO);
        assertEquals(38029, decoded.frequency);
        assertArrayEquals(new int[]{421, 841, 1262, 1683}, decoded.pattern());
        Signal compatibility = ProntoDecoder.decode(PRONTO, true);
        assertArrayEquals(new int[]{416, 832, 1248, 1664}, compatibility.pattern());
        assertEquals(38028, compatibility.frequency);
    }
    @Test public void legacyTimingAllowsObservedHeaderCountMismatchButStandardRejectsIt() {
        String mismatch = PRONTO.replace("0001 0001", "0001 0000");
        assertThrows(IllegalArgumentException.class, () -> ProntoDecoder.decode(mismatch));
        assertEquals(4, ProntoDecoder.decode(mismatch, true).pattern().length);
        for (String bad : new String[]{"", PRONTO.replace("0000 006D", "0100 006D"),
                PRONTO.replace("006D", "0000"), PRONTO + " 0001", PRONTO.replace("0010", "0000"),
                PRONTO.replace("0010", "GGGG"), "0000 006D 0000 0001 00AB 0F8D", PRONTO.replace("0010", "FFFF").replace("0020", "FFFF")}) {
            assertThrows(IllegalArgumentException.class, () -> ProntoDecoder.decode(bad, true));
        }
    }
    @Test public void profileMetadataAndLazySignalsPreserveCandidatesWithOneBadCommand() throws Exception {
        JSONObject raw = profile().put("timing", "acremote26").put("profileId", "example/candidate1");
        raw.getJSONObject("commands").put("BROKEN", "").put("BAD_HEADER", "not pronto")
            .put("TIME_OFF_5.5", PRONTO).put("(H28FA", PRONTO);
        LibraryProfile p = LibraryProfile.parse(raw);
        assertEquals(16, p.minTemperature); assertEquals(30, p.maxTemperature); assertEquals(3, p.maxFan);
        assertEquals(Arrays.asList("C", "H", "W", "D", "A"), p.modes);
        assertTrue(p.hasTemperatureRange); assertTrue(p.available("POWER_ON")); assertFalse(p.available("BROKEN"));
        assertEquals(416, p.signal("POWER_ON").pattern()[0]);
        assertTrue(p.available("TIME_OFF_5.5")); assertTrue(p.available("(H28FA"));
        assertThrows(IllegalArgumentException.class, () -> p.signal("BAD_HEADER"));
        assertThrows(IllegalArgumentException.class, () -> p.signal("MISSING"));
        assertThrows(UnsupportedOperationException.class, () -> p.commands.put("X", PRONTO));
        LibraryProfile noMetadata = LibraryProfile.parse(profile().put("metadata", new JSONObject()));
        assertFalse(noMetadata.hasTemperatureRange); assertEquals(16, noMetadata.minTemperature);
        assertTrue(noMetadata.modes.isEmpty());
        JSONObject fahrenheit = profile(); fahrenheit.getJSONObject("metadata").put("degree_type", "F").put("min_degree", 60).put("max_degree", 86);
        assertEquals("F", LibraryProfile.parse(fahrenheit).degreeType);
    }
    @Test public void rejectsOutOfRangeAndMalformedMetadata() throws Exception {
        for (String[] change : new String[][]{{"min_degree", "99"}, {"max_fan_power", "100"},
                {"min_degree", "16.5"}, {"degree_type", "K"}, {"modes", "C,C"}, {"modes", "UNKNOWN"}}) {
            JSONObject raw = profile(); raw.getJSONObject("metadata").put(change[0], change[1]);
            assertThrows(IllegalArgumentException.class, () -> LibraryProfile.parse(raw));
        }
        JSONObject commands = new JSONObject(); for (int i = 0; i < 2049; i++) commands.put("X" + i, PRONTO);
        assertThrows(IllegalArgumentException.class, () -> LibraryProfile.parse(profile().put("commands", commands)));
    }
    @Test public void catalogSupportsMoreCandidatesThanSavedDeviceLimitAndOwnsItsEntries() throws Exception {
        File file = zip(contents(51));
        try (LibraryCatalog catalog = LibraryCatalog.open(file); LibraryCatalog other = LibraryCatalog.open(file)) {
            assertEquals(51, catalog.brands.get(0).profiles.size());
            LibraryCatalog.Entry entry = catalog.brands.get(0).profiles.get(50);
            assertSame(entry, catalog.find(entry.entry));
            assertEquals("Example", catalog.load(entry).brand);
            assertThrows(IOException.class, () -> other.load(entry));
            assertThrows(UnsupportedOperationException.class, () -> catalog.brands.clear());
            assertThrows(UnsupportedOperationException.class, () -> catalog.brands.get(0).profiles.clear());
        }
        LibraryCatalog closed = LibraryCatalog.open(file); LibraryCatalog.Entry entry = closed.brands.get(0).profiles.get(0); closed.close();
        assertThrows(IOException.class, () -> closed.load(entry));
    }
    @Test public void rejectsUnsafeDuplicateAndMissingZipEntries() throws Exception {
        for (String path : new String[]{"../outside.json", "/absolute", "profiles/../evil", "profiles\\evil", "C:evil", "a//b"}) {
            Map<String, String> entries = contents(1); entries.put(path, "x"); File file = zip(entries);
            assertThrows(IOException.class, () -> LibraryCatalog.open(file));
        }
        Map<String, String> missing = contents(1); missing.remove("profiles/example/candidate0.json"); File missingFile = zip(missing);
        assertThrows(IOException.class, () -> LibraryCatalog.open(missingFile));
        Map<String, String> duplicates = contents(1); duplicates.put("other.json", "{}"); File duplicateFile = zip(duplicates);
        byte[] bytes = Files.readAllBytes(duplicateFile.toPath());
        byte[] old = "other.json".getBytes(StandardCharsets.UTF_8), replacement = "index.json".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= bytes.length - old.length; i++) {
            boolean matches = true; for (int j = 0; j < old.length; j++) if (bytes[i + j] != old[j]) { matches = false; break; }
            if (matches) System.arraycopy(replacement, 0, bytes, i, replacement.length);
        }
        Files.write(duplicateFile.toPath(), bytes);
        assertThrows(IOException.class, () -> LibraryCatalog.open(duplicateFile));
    }
    @Test public void boundsExpandedProfileSizeAndIndexVersion() throws Exception {
        Map<String, String> oversized = contents(1);
        char[] large = new char[4 * 1024 * 1024 + 1]; Arrays.fill(large, ' ');
        oversized.put("profiles/example/candidate0.json", new String(large)); File oversizedFile = zip(oversized);
        assertThrows(IOException.class, () -> LibraryCatalog.open(oversizedFile));
        Map<String, String> version = contents(1); version.put("index.json", index(1).put("version", 2).toString()); File versionFile = zip(version);
        assertThrows(IOException.class, () -> LibraryCatalog.open(versionFile));
        JSONObject duplicate = index(1); JSONArray candidates = duplicate.getJSONArray("brands").getJSONObject(0).getJSONArray("profiles"); candidates.put(candidates.getJSONObject(0));
        Map<String, String> duplicateIds = contents(1); duplicateIds.put("index.json", duplicate.toString()); File duplicateIdsFile = zip(duplicateIds);
        assertThrows(IOException.class, () -> LibraryCatalog.open(duplicateIdsFile));
    }
}
