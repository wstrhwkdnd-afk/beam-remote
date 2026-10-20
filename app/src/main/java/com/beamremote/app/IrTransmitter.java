package com.beamremote.app;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IrTransmitter {
    public interface Callback { void done(boolean success, String message); }
    private final ConsumerIrManager manager;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    // Shared across activity recreation so rotation cannot overlap an in-flight send.
    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean closed;
    public IrTransmitter(Context context) { manager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE); }
    public boolean available() {
        try { return manager != null && manager.hasIrEmitter(); }
        catch (RuntimeException e) { return false; }
    }
    public String capabilities() {
        if (!available()) return "No Android-accessible IR emitter. Preview is available; no signals will be sent.";
        try {
            ConsumerIrManager.CarrierFrequencyRange[] ranges = manager.getCarrierFrequencies();
            if (ranges == null || ranges.length == 0) return "IR emitter found. Supported carrier frequencies were not reported.";
            StringBuilder result = new StringBuilder("IR emitter found. Carrier ranges (Hz): ");
            for (ConsumerIrManager.CarrierFrequencyRange range : ranges) result.append(range.getMinFrequency()).append('–').append(range.getMaxFrequency()).append("  ");
            return result.toString();
        } catch (RuntimeException e) { return "IR emitter found. Frequency query unavailable."; }
    }
    public void send(Signal signal, boolean preview, Callback callback) {
        if (closed) return;
        if (!busy.compareAndSet(false, true)) { callback.done(false, "Please wait for the previous signal."); return; }
        worker.execute(() -> {
            boolean success = false;
            String message;
            try {
                if (preview) {
                    message = "Preview only · " + signal.frequency + " Hz · " + signal.pattern().length + " durations · " + signal.durationMicros() / 1000 + " ms. Nothing transmitted.";
                } else {
                    if (!available()) throw new IllegalStateException("No accessible IR emitter. Use Preview or a compatible phone.");
                    ConsumerIrManager.CarrierFrequencyRange[] ranges = null;
                    try { ranges = manager.getCarrierFrequencies(); } catch (RuntimeException ignored) { /* Some OEMs do not expose ranges. */ }
                    if (ranges != null && ranges.length > 0) {
                        boolean supported = false;
                        for (ConsumerIrManager.CarrierFrequencyRange range : ranges)
                            supported |= signal.frequency >= range.getMinFrequency() && signal.frequency <= range.getMaxFrequency();
                        if (!supported) throw new IllegalArgumentException("This phone does not support " + signal.frequency + " Hz.");
                    }
                    manager.transmit(signal.frequency, signal.pattern());
                    message = "Signal sent. Check the appliance — IR provides no confirmation.";
                    success = true;
                }
                // Drop rapid taps rather than accumulating stale power or temperature commands.
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); message = "Transmission interrupted.";
            } catch (RuntimeException e) { message = "Could not send: " + e.getMessage(); }
            final String result = message; final boolean sent = success;
            busy.set(false);
            main.post(() -> { if (!closed) callback.done(sent, result); });
        });
    }
    public void close() { closed = true; worker.shutdown(); main.removeCallbacksAndMessages(null); }
}
