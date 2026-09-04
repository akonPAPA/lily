package os.companion.character.behaviour;

import os.companion.ai.VisionModel;
import os.companion.nativeplatform.ScreenGrabber;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class ScreenPerception {

    private static final int MAX_DIM = 768;
    private static final double ACTIVE_INTERVAL_MS = 12_000;
    private static final double AMBIENT_INTERVAL_MS = 45_000;
    private static final Duration DESCRIBE_TIMEOUT = Duration.ofSeconds(20);

    private final ScreenGrabber grabber;
    private final VisionModel vision;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "screen-vision");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean enabled = false;
    private volatile String latest = null;
    private double accumMs = 0;
    private BiConsumer<String, Boolean> onDescribed;

    public ScreenPerception(ScreenGrabber grabber, VisionModel vision) {
        this.grabber = grabber;
        this.vision = vision;
    }

    public boolean isAvailable() {
        return grabber != null && grabber.isAvailable();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            latest = null;
        }
    }

    public boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    public String latest() {
        return latest;
    }

    public void setOnDescribed(BiConsumer<String, Boolean> onDescribed) {
        this.onDescribed = onDescribed;
    }

    public void tick(double deltaMs, boolean active) {
        if (!enabled || !isAvailable()) {
            return;
        }
        accumMs += deltaMs;
        double interval = active ? ACTIVE_INTERVAL_MS : AMBIENT_INTERVAL_MS;
        if (accumMs < interval) {
            return;
        }
        accumMs = 0;
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker.submit(() -> {
            try {
                if (!vision.ensureReady()) {
                    return;
                }
                String url = grabber.captureForegroundDataUrl(MAX_DIM);
                if (url == null) {
                    return;
                }
                String desc = vision.describe(url, DESCRIBE_TIMEOUT);
                if (desc == null) {
                    return;
                }
                boolean changed = latest == null || !normalize(desc).equals(normalize(latest));
                latest = desc;
                if (onDescribed != null) {
                    onDescribed.accept(desc, changed);
                }
            } finally {
                running.set(false);
            }
        });
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    public void close() {
        worker.shutdownNow();
    }
}
