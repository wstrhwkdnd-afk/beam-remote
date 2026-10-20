package com.beamremote.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded, random-access optional library. ZIP entries are never extracted to disk. */
public final class LibraryCatalog implements Closeable {
    public static final long MAX_ZIP_BYTES = 64L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_PROFILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_INDEX_BYTES = 2 * 1024 * 1024;
    public static final class Entry {
        public final String id, name, entry;
        private final LibraryCatalog owner;
        private Entry(LibraryCatalog owner, String id, String name, String entry) {
            this.owner = owner; this.id = id; this.name = name; this.entry = entry;
        }
    }
    public static final class Brand {
        public final String id, name;
        public final List<Entry> profiles;
        private Brand(String id, String name, List<Entry> profiles) {
            this.id = id; this.name = name;
            this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
        }
    }
    public final List<Brand> brands;
    public final String source;
    private final ZipFile zip;
    private final Map<String, ZipEntry> entries = new HashMap<>();
    private final Map<String, Entry> profiles = new HashMap<>();
    private boolean closed;
    public static LibraryCatalog open(File file) throws IOException {
        if (!file.isFile() || file.length() == 0 || file.length() > MAX_ZIP_BYTES)
            throw new IOException("Library must be a ZIP file no larger than 64 MiB.");
        ZipFile archive = new ZipFile(file);
        try { return new LibraryCatalog(archive); }
        catch (IOException | RuntimeException failure) {
            try { archive.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            throw failure;
        }
    }
    private LibraryCatalog(ZipFile zip) throws IOException {
        this.zip = zip;
        long expanded = 0; int entryCount = 0;
        Enumeration<? extends ZipEntry> enumeration = zip.entries();
        Set<String> names = new HashSet<>();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            String name = entry.getName();
            safePath(name, entry.isDirectory());
            String unique = entry.isDirectory() ? name.substring(0, name.length() - 1) : name;
            if (!names.add(unique) || ++entryCount > 50000) throw new IOException("Duplicate or excessive ZIP entries.");
            long size = entry.getSize();
            if (size < 0 || size > MAX_EXPANDED_BYTES || (expanded += size) > MAX_EXPANDED_BYTES)
                throw new IOException("Expanded library exceeds 256 MiB.");
            if (!entry.isDirectory()) entries.put(name, entry);
        }
        JSONObject index = json(read("index.json", MAX_INDEX_BYTES));
        if (!"beam-profile-library".equals(index.opt("format")) || !Integer.valueOf(1).equals(index.opt("version")))
            throw new IOException("Unsupported profile library format or version.");
        source = LibraryProfile.text(index, "source", 240);
        JSONArray brandRows = index.optJSONArray("brands");
        if (brandRows == null || brandRows.length() == 0 || brandRows.length() > 1000)
            throw new IOException("Library needs 1–1,000 brands.");
        List<Brand> result = new ArrayList<>();
        Set<String> brandIds = new HashSet<>(), referencedPaths = new HashSet<>();
        int count = 0;
        for (int i = 0; i < brandRows.length(); i++) {
            JSONObject row = brandRows.optJSONObject(i);
            if (row == null) throw new IOException("Invalid brand entry.");
            String id = LibraryProfile.text(row, "id", 160), name = LibraryProfile.text(row, "name", 120);
            if (!brandIds.add(id)) throw new IOException("Duplicate brand ID.");
            JSONArray candidateRows = row.optJSONArray("profiles");
            if (candidateRows == null || candidateRows.length() == 0) throw new IOException("Brand has no profiles.");
            List<Entry> candidates = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (int j = 0; j < candidateRows.length(); j++) {
                if (++count > 20000) throw new IOException("Library exceeds 20,000 profiles.");
                JSONObject candidate = candidateRows.optJSONObject(j);
                if (candidate == null) throw new IOException("Invalid profile entry.");
                String candidateId = LibraryProfile.text(candidate, "id", 160);
                String candidateName = LibraryProfile.text(candidate, "name", 160);
                String path = LibraryProfile.text(candidate, "entry", 400);
                safePath(path, false);
                ZipEntry item = entries.get(path);
                if (!path.startsWith("profiles/") || !path.endsWith(".json") || item == null || item.getSize() > MAX_PROFILE_BYTES)
                    throw new IOException("Missing or oversized profile entry.");
                if (!ids.add(candidateId) || !referencedPaths.add(path)) throw new IOException("Duplicate profile ID or path.");
                Entry entry = new Entry(this, candidateId, candidateName, path);
                candidates.add(entry); profiles.put(path, entry);
            }
            result.add(new Brand(id, name, candidates));
        }
        brands = Collections.unmodifiableList(result);
    }
    private static void safePath(String name, boolean directory) throws IOException {
        if (name == null || name.isEmpty() || name.length() > 400 || name.startsWith("/") || name.contains("\\") || name.contains(":"))
            throw new IOException("Unsafe ZIP path.");
        String path = directory ? name.substring(0, name.length() - 1) : name;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe ZIP path.");
            for (int i = 0; i < part.length(); i++) if (Character.isISOControl(part.charAt(i))) throw new IOException("Unsafe ZIP path.");
        }
    }
    private static JSONObject json(String text) throws IOException {
        try { return new JSONObject(text); }
        catch (Exception exception) { throw new IOException("Invalid library JSON.", exception); }
    }
    private String read(String path, int limit) throws IOException {
        if (closed) throw new IOException("Library is closed.");
        ZipEntry entry = entries.get(path);
        if (entry == null || entry.getSize() > limit) throw new IOException("Missing or oversized library entry: " + path);
        try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int read, total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("Library entry exceeds its size limit.");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    public LibraryProfile load(Entry entry) throws IOException {
        if (entry == null || entry.owner != this || profiles.get(entry.entry) != entry)
            throw new IOException("Profile does not belong to this library.");
        try { return LibraryProfile.parse(json(read(entry.entry, MAX_PROFILE_BYTES))); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid profile: " + entry.name + ". " + exception.getMessage(), exception); }
    }
    public Entry find(String path) { return profiles.get(path); }
    @Override public void close() throws IOException { if (!closed) { closed = true; zip.close(); } }
}
