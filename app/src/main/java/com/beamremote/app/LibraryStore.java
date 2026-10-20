package com.beamremote.app;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;

/** Optional libraries are copied into app-private storage and bound by content digest. */
public final class LibraryStore {
    private final Context context;
    private final File directory;
    public LibraryStore(Context context) {
        this.context = context; directory = new File(context.getFilesDir(), "libraries");
    }
    public String selected() { return context.getSharedPreferences("libraries", 0).getString("selected", ""); }
    public File file(String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Import the referenced profile library first.");
        File file = new File(directory, id + ".beamzip");
        if (!file.isFile()) throw new IOException("This remote needs its original profile library. Import that library again.");
        return file;
    }
    public String install(InputStream input) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create library storage.");
        File temp = File.createTempFile("incoming-", ".tmp", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream out = new FileOutputStream(temp)) {
                byte[] block = new byte[32768]; int count; long total = 0;
                while ((count = input.read(block)) != -1) {
                    if ((total += count) > LibraryCatalog.MAX_ZIP_BYTES) throw new IOException("Library exceeds 64 MiB.");
                    digest.update(block, 0, count); out.write(block, 0, count);
                }
            }
            try (LibraryCatalog catalog = LibraryCatalog.open(temp)) {
                // Validate the catalog before making it visible; command data stays lazy and bounded.
                if (catalog.brands.isEmpty()) throw new IOException("Library has no brands.");
            }
            StringBuilder key = new StringBuilder(); for (byte b : digest.digest()) key.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            String id = key.toString(); File target = new File(directory, id + ".beamzip");
            if (!target.exists()) {
                File[] installed = directory.listFiles((dir, name) -> name.endsWith(".beamzip"));
                if (installed != null && installed.length >= 5) throw new IOException("Five libraries are already installed. Existing saved remotes keep their original libraries.");
                if (!temp.renameTo(target)) throw new IOException("Cannot save imported library.");
            }
            if (!context.getSharedPreferences("libraries", 0).edit().putString("selected", id).commit())
                throw new IOException("Library saved, but selection could not be stored. Import it again.");
            return id;
        } finally { if (temp.exists()) temp.delete(); }
    }
}
