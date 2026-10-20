package com.beamremote.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android widgets, no web view, accounts, network access, or third-party UI dependencies. */
public final class MainActivity extends Activity {
    private static final int GREEN = 0xFF176B5B, INK = 0xFF18342D, MUTED = 0xFF596B63, PAPER = 0xFFF5F4EF;
    private static final int IMPORT = 41, EXPORT = 42;
    private ProfileStore store;
    private IrTransmitter transmitter;
    private List<Profile> profiles = new ArrayList<>();
    private Profile current;
    private LinearLayout page;
    private TextView status;
    private String screen = "devices", pendingExport, startupError;
    private boolean preview, storageHealthy = true;
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ProfileStore(this); transmitter = new IrTransmitter(getApplicationContext());
        preview = getPreferences(MODE_PRIVATE).getBoolean("preview", !transmitter.available());
        try { profiles = store.load(); }
        catch (Exception e) { storageHealthy = false; startupError = "Saved profiles could not be read. Existing data has been preserved. " + e.getMessage(); }
        for (Profile p : profiles) if (p.id.equals(store.selected())) current = p;
        if (current == null && !profiles.isEmpty()) current = profiles.get(0);
        if (state != null) { screen = state.getString("screen", "devices"); pendingExport = state.getString("export"); }
        render();
        if (startupError != null) alert("Storage problem", startupError);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("screen", screen); out.putString("export", pendingExport);
    }
    @Override protected void onDestroy() { transmitter.close(); fileWorker.shutdown(); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(7)); parent.addView(view); return view;
    }
    private void title(LinearLayout parent, String value, int size) { text(parent, value, size, INK).setTypeface(null, Typeface.BOLD); }
    private LinearLayout card() {
        LinearLayout card = column(); card.setPadding(dp(18), dp(14), dp(18), dp(18)); card.setBackground(background(Color.WHITE, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(12), 0, dp(4));
        page.addView(card, params); return card;
    }
    private Button button(LinearLayout parent, String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : GREEN); button.setBackgroundTintList(null);
        button.setBackground(background(primary ? GREEN : 0xFFE8F1EB, 16)); button.setMinHeight(dp(56));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(dp(3), dp(5), dp(3), dp(5));
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) { params.width = 0; params.weight = 1; }
        parent.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
    }
    private LinearLayout row(LinearLayout parent) { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); parent.addView(row); return row; }
    private void render() {
        LinearLayout shell = column(); shell.setBackgroundColor(PAPER);
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        page = column(); page.setPadding(dp(20), dp(16), dp(20), dp(24)); scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = row(shell); nav.setPadding(dp(8), dp(4), dp(8), dp(6));
        button(nav, "Devices", screen.equals("devices"), () -> { screen = "devices"; render(); });
        button(nav, "Remote", screen.equals("remote"), () -> { screen = "remote"; render(); });
        button(nav, "Help", screen.equals("help"), () -> { screen = "help"; render(); });
        setContentView(shell); shell.requestApplyInsets();
        text(page, "B E A M   /   REMOTE", 13, GREEN).setTypeface(null, Typeface.BOLD);
        if (screen.equals("help")) { help(); return; }
        title(page, screen.equals("devices") ? "A little more control." : current == null ? "Your remote" : current.name, 30);
        text(page, preview ? "PREVIEW MODE · No infrared will be sent" : transmitter.available() ? "IR ready · Point the top of your phone at the appliance" : "IR unavailable · Switch to Preview in Help", 14, MUTED);
        if (screen.equals("devices")) devices(); else remote();
    }
    private void devices() {
        text(page, "Your rooms. Your devices. One remote.", 16, MUTED);
        if (profiles.isEmpty()) {
            LinearLayout intro = card(); title(intro, "Meet your next remote", 23);
            text(intro, "Add a TV or air conditioner, choose a code family, then try a command. You can keep a separate profile for every room.", 16, MUTED);
        }
        for (Profile p : profiles) {
            LinearLayout c = card(); text(c, p.isAc() ? "AIR CONDITIONER" : "TELEVISION", 12, GREEN);
            title(c, p.name, 23); text(c, p.brand + " · " + p.model, 14, MUTED);
            button(c, "Open remote", true, () -> { current = p; store.select(p.id); screen = "remote"; render(); });
        }
        button(page, "+ Add device", true, this::addDevice);
        button(page, "Import a profile", false, () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, IMPORT);
        });
    }
    private void remote() {
        if (current == null) { text(page, "Add a device to build your first remote.", 18, MUTED); button(page, "+ Add device", true, this::addDevice); return; }
        Profile p = current;
        text(page, p.brand + " · " + p.model, 15, MUTED);
        LinearLayout panel = card();
        if (p.hasAcEncoder()) acControls(panel, p);
        else {
            title(panel, p.isAc() ? "Your AC scenes" : "Everyday controls", 21);
            if (p.commands.isEmpty()) text(panel, p.isAc() ? "Add complete AC signals, such as “Cool · 24°C · Auto fan” and “Off”." : "Add a command below or import a profile with codes for your model.", 16, MUTED);
            List<Map.Entry<String, Signal>> codes = new ArrayList<>(p.commands.entrySet());
            for (int i = 0; i < codes.size(); i += 2) {
                LinearLayout r = row(panel);
                for (int j = i; j < Math.min(i + 2, codes.size()); j++) {
                    Map.Entry<String, Signal> entry = codes.get(j);
                    button(r, entry.getKey(), entry.getKey().equals("Power"), () -> send(entry.getValue()));
                }
            }
        }
        status = text(panel, "Ready when you are. Appliance state is not known.", 14, MUTED);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout manage = card(); title(manage, "Make it yours", 20);
        text(manage, "Code families are candidates, not guaranteed matches. Test with the appliance in view.", 14, MUTED);
        button(manage, "Edit name, brand & model", false, this::editProfile);
        if (!p.hasAcEncoder()) button(manage, "Add or edit IR codes", false, this::chooseCommand);
        button(manage, "Export profile", false, this::exportProfile);
        button(manage, "Delete device", false, () -> new AlertDialog.Builder(this).setTitle("Delete " + p.name + "?")
            .setMessage("This removes the saved profile from this phone.").setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (d, w) -> {
                List<Profile> next = new ArrayList<>(profiles); next.remove(p);
                if (persist(next)) { profiles = next; current = profiles.isEmpty() ? null : profiles.get(0); store.select(current == null ? "" : current.id); screen = "devices"; render(); }
            }).show());
    }
    private void acControls(LinearLayout panel, Profile p) {
        text(panel, "DESIRED SETTINGS · NOT LIVE STATUS", 12, GREEN);
        TextView temp = text(panel, p.temperature + "°C", 52, INK); temp.setGravity(Gravity.CENTER); temp.setTypeface(null, Typeface.BOLD);
        LinearLayout r = row(panel);
        button(r, "−  Cooler", false, () -> { p.temperature = Math.max(p.minTemperature(), p.temperature - 1); p.acCommand = 2; persist(profiles); temp.setText(p.temperature + "°C"); });
        button(r, "+  Warmer", false, () -> { p.temperature = Math.min(p.maxTemperature(), p.temperature + 1); p.acCommand = 2; persist(profiles); temp.setText(p.temperature + "°C"); });
        text(panel, "Mode", 14, MUTED);
        int[] modes = p.isWhirlpool() ? new int[]{0, 1, 2, 4} : new int[]{0, 1, 2, 3, 4};
        Spinner mode = spinner(panel, p.isWhirlpool() ? new String[]{"Cool", "Dry", "Fan", "Heat"} : new String[]{"Cool", "Dry", "Fan", "Auto", "Heat"});
        mode.setContentDescription("AC mode");
        for (int i = 0; i < modes.length; i++) if (modes[i] == p.mode) mode.setSelection(i);
        mode.setOnItemSelectedListener(listener(i -> { if (p.mode != modes[i]) { p.mode = modes[i]; p.acCommand = 6; persist(profiles); } }));
        text(panel, "Fan speed", 14, MUTED);
        int[] fans = p.isWhirlpool() ? new int[]{5, 1, 2, 4} : new int[]{5, 0, 1, 2, 4};
        Spinner fan = spinner(panel, p.isWhirlpool() ? new String[]{"Auto", "Low", "Medium", "High"} : new String[]{"Auto", "Lowest", "Low", "Medium", "High"});
        fan.setContentDescription("Fan speed");
        for (int i = 0; i < fans.length; i++) if (fans[i] == p.fan) fan.setSelection(i);
        fan.setOnItemSelectedListener(listener(i -> { if (p.fan != fans[i]) { p.fan = fans[i]; p.acCommand = 17; persist(profiles); } }));
        if (p.isWhirlpool()) {
            text(panel, "Changing a setting does not send it. Apply sends the complete settings without toggling power. Use Power on/off once to change the appliance’s power.", 14, MUTED);
            button(panel, "Apply settings", true, () -> send(p.acSignal(false)));
            button(panel, "Power on/off (toggle)", false, () -> send(p.acSignal(true)));
            text(panel, "Whirlpool candidate · " + p.minTemperature() + "–" + p.maxTemperature() + "°C. Heat requires a heating-capable AC. 6th Sense, swing, sleep, turbo and timers are not included; use custom captured scenes for those features. Appliance state is unknown.", 14, MUTED);
        } else {
            text(panel, "Changing a setting does not send it. “Turn on / Apply” sends all settings together.", 14, MUTED);
            button(panel, "Turn on / Apply settings", true, () -> send(p.acSignal(true)));
            button(panel, "Turn off", false, () -> send(p.acSignal(false)));
        }
    }
    private interface Selection { void choose(int index); }
    private AdapterView.OnItemSelectedListener listener(Selection selected) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selected.choose(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
    }
    private void send(Signal signal) {
        TextView target = status;
        target.setText(preview ? "Preparing preview…" : "Sending…");
        transmitter.send(signal, preview, (sent, message) -> target.setText(message));
    }
    private void help() {
        title(page, "A clear connection.", 30);
        LinearLayout c = card(); title(c, "This phone", 22); text(c, transmitter.capabilities(), 16, MUTED);
        Switch toggle = new Switch(this); toggle.setText("Preview mode"); toggle.setTextSize(18); toggle.setMinHeight(dp(56)); toggle.setChecked(preview); c.addView(toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> { preview = checked; getPreferences(MODE_PRIVATE).edit().putBoolean("preview", checked).apply(); });
        text(c, "Preview validates and describes a signal without transmitting it. It never changes the appliance.", 15, MUTED);
        LinearLayout tips = card(); title(tips, "Start here", 22);
        text(tips, "1. Add a device and pick its remote code family. A remote model is often printed inside its battery cover.\n\n2. Point your phone’s IR window at the appliance. Start close, with clear line of sight.\n\n3. Try one command. If it does not work, select a different profile or import verified codes.\n\n4. Rename a working profile for its room.", 16, MUTED);
        LinearLayout limits = card(); title(limits, "What to expect", 22);
        text(limits, "A built-in IR emitter must be exposed through Android’s ConsumerIrManager. Some phones require unsupported vendor APIs. An emulator cannot emit IR.\n\nIR is one-way: “sent” does not mean the appliance responded. Power toggles may become out of sync.\n\nAC protocols vary by model. LG presets cover basic power, temperature, mode and fan; swing, timers and special features are not included. Whirlpool presets support basic settings and a power toggle for DG11J1 remote families. For other models or advanced functions, import complete raw state scenes.\n\nYour phone cannot learn a remote through this API. Capturing codes requires a separate IR receiver. Bluetooth, RF and Wi-Fi remotes are not supported.\n\nProfiles stay on this phone. No account, network permission, or analytics.", 16, MUTED);
    }
    private LinearLayout form() { LinearLayout form = column(); form.setPadding(dp(22), dp(8), dp(22), dp(12)); return form; }
    private EditText field(LinearLayout form, String label, String initial) {
        text(form, label, 14, MUTED); EditText input = new EditText(this); input.setText(initial); input.setSingleLine(true); input.setMinHeight(dp(48));
        input.setContentDescription(label); form.addView(input); return input;
    }
    private Spinner spinner(LinearLayout parent, String[] choices) {
        Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); spinner.setMinimumHeight(dp(52)); parent.addView(spinner); return spinner;
    }
    private AlertDialog formDialog(String title, LinearLayout form, String positive) {
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel", null).setPositiveButton(positive, null).create();
        dialog.show(); return dialog;
    }
    private void addDevice() {
        LinearLayout f = form(); EditText name = field(f, "Device name", "Living room");
        text(f, "Brand / remote code family", 14, MUTED); Spinner preset = spinner(f, Catalog.OPTIONS);
        preset.setContentDescription("Brand and remote code family");
        text(f, "Choose the closest candidate. Other brands can use imported or manually entered codes.", 14, MUTED);
        AlertDialog d = formDialog("Add a device", f, "Add device");
        d.getButton(-1).setOnClickListener(v -> {
            String label = name.getText().toString().trim();
            if (label.isEmpty() || label.length() > 60) { name.setError("Enter a name of 1–60 characters"); return; }
            if (profiles.size() >= 50) { alert("Profile limit", "Export or remove a device before adding more than 50 profiles."); return; }
            Profile p = Catalog.create(preset.getSelectedItemPosition(), label);
            List<Profile> next = new ArrayList<>(profiles); next.add(p);
            if (persist(next)) { profiles = next; current = p; store.select(p.id); screen = "remote"; d.dismiss(); render(); }
        });
    }
    private void editProfile() {
        Profile p = current; LinearLayout f = form(); EditText name = field(f, "Name", p.name);
        EditText brand = field(f, "Brand", p.brand); EditText model = field(f, "Model / remote model", p.model);
        text(f, "These labels do not change the signal protocol. Add a new device to use another code family.", 14, MUTED);
        AlertDialog d = formDialog("Device details", f, "Save");
        d.getButton(-1).setOnClickListener(v -> {
            try {
                JSONObject data = p.json().put("name", name.getText().toString().trim()).put("brand", brand.getText().toString().trim()).put("model", model.getText().toString().trim());
                Profile updated = Profile.parse(data); List<Profile> next = new ArrayList<>(profiles); next.set(profiles.indexOf(p), updated);
                if (persist(next)) { profiles = next; current = updated; d.dismiss(); render(); }
            } catch (Exception e) { alert("Check the details", e.getMessage()); }
        });
    }
    private void chooseCommand() {
        List<String> keys = new ArrayList<>(current.commands.keySet()); keys.add(0, "+ New command / scene");
        new AlertDialog.Builder(this).setTitle("IR codes").setItems(keys.toArray(new String[0]), (dialog, which) -> editCommand(which == 0 ? null : keys.get(which))).show();
    }
    private void editCommand(String key) {
        Profile p = current; Signal old = key == null ? null : p.commands.get(key);
        LinearLayout f = form(); EditText name = field(f, "Button label", key == null ? "" : key);
        EditText frequency = field(f, "Carrier frequency (Hz)", String.valueOf(old == null ? 38000 : old.frequency)); frequency.setInputType(InputType.TYPE_CLASS_NUMBER);
        StringBuilder joiner = new StringBuilder();
        if (old != null) for (int value : old.pattern()) { if (joiner.length() > 0) joiner.append(", "); joiner.append(value); }
        EditText raw = field(f, "Alternating mark / space durations (µs)", joiner.toString()); raw.setSingleLine(false); raw.setMinLines(4); raw.setMaxLines(8); raw.setGravity(Gravity.TOP);
        text(f, "Start with a mark. Paste positive decimal microseconds, separated by commas or spaces. Omit the receiver’s leading idle gap. Include any required repeats and gaps in this pattern. No hex or Pronto format.", 14, MUTED);
        AlertDialog d = formDialog("Edit IR command", f, "Save code");
        d.getButton(-1).setOnClickListener(v -> {
            try {
                String label = name.getText().toString().trim();
                if (label.isEmpty() || label.length() > 60) throw new IllegalArgumentException("Use a label of 1–60 characters.");
                if (!label.equals(key) && p.commands.containsKey(label)) throw new IllegalArgumentException("That button name already exists. Edit it from the list.");
                Profile updated = Profile.parse(p.json());
                if (key != null) updated.commands.remove(key);
                if (updated.commands.size() >= 64) throw new IllegalArgumentException("Maximum 64 commands per profile.");
                updated.commands.put(label, Signal.parse(Integer.parseInt(frequency.getText().toString()), raw.getText().toString()));
                List<Profile> next = new ArrayList<>(profiles); next.set(profiles.indexOf(p), updated);
                if (persist(next)) { profiles = next; current = updated; d.dismiss(); render(); }
            } catch (Exception e) { alert("Check the code", e.getMessage()); }
        });
        if (key != null) button(f, "Remove this command", false, () -> {
            try {
                Profile updated = Profile.parse(p.json()); updated.commands.remove(key);
                List<Profile> next = new ArrayList<>(profiles); next.set(profiles.indexOf(p), updated);
                if (persist(next)) { profiles = next; current = updated; d.dismiss(); render(); }
            } catch (Exception e) { alert("Could not remove code", e.getMessage()); }
        });
    }
    private boolean persist(List<Profile> next) {
        if (!storageHealthy) { alert("Storage unavailable", "Existing unreadable data has been preserved. Restore a backup or clear app storage in Android Settings to start again."); return false; }
        try { store.save(next); return true; } catch (RuntimeException e) { alert("Could not save", e.getMessage()); return false; }
    }
    private void exportProfile() {
        try {
            pendingExport = current.id;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "beam-profile.json");
            startActivityForResult(intent, EXPORT);
        } catch (Exception e) { alert("Could not export", e.getMessage()); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request != IMPORT && request != EXPORT) return;
        final android.net.Uri uri = data.getData();
        String snapshot = null;
        try {
            if (request == EXPORT) {
                for (Profile p : profiles) if (p.id.equals(pendingExport)) snapshot = p.json().toString(2);
                if (snapshot == null) throw new IllegalStateException("Please export the profile again.");
                if (snapshot.getBytes(StandardCharsets.UTF_8).length > 524288)
                    throw new IllegalArgumentException("Profile exceeds the 512 KB transfer limit. Split it into smaller profiles.");
            }
        } catch (Exception e) { alert("Could not export", e.getMessage()); return; }
        final String exportData = snapshot;
        Toast.makeText(this, request == IMPORT ? "Reading profile…" : "Exporting profile…", Toast.LENGTH_SHORT).show();
        fileWorker.execute(() -> {
          try {
            if (request == EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Cannot open destination.");
                    out.write(exportData.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> { if (!isDestroyed()) { pendingExport = null; Toast.makeText(this, "Profile exported", Toast.LENGTH_SHORT).show(); } });
            } else if (request == IMPORT) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Cannot open profile.");
                    byte[] block = new byte[4096]; int read;
                    while ((read = in.read(block)) != -1) {
                        if (buffer.size() + read > 524288) throw new IllegalArgumentException("Profile must be smaller than 512 KB.");
                        buffer.write(block, 0, read);
                    }
                }
                Profile imported = Profile.parse(new JSONObject(buffer.toString("UTF-8"))); imported.id = UUID.randomUUID().toString();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (profiles.size() >= 50) { alert("Profile limit", "Maximum 50 profiles. Remove a device first."); return; }
                    List<Profile> next = new ArrayList<>(profiles); next.add(imported);
                    if (persist(next)) { profiles = next; current = imported; store.select(imported.id); screen = "remote"; render(); }
                });
            }
          } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) alert("Could not read or write profile", e.getMessage()); }); }
        });
    }
    private void alert(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message == null ? "Please check your input and try again." : message).setPositiveButton("OK", null).show(); }
}
