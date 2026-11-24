package dev.skhanal.atrium.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps the generated rooms, and builds the ones it does not have yet.
 *
 * Generating a room touches 150k pixels, which is far too slow for a frame, so
 * it happens on one background thread. Only the most recent request is kept
 * waiting: when somebody runs along the row of apps, the rooms they passed
 * through are no longer wanted by the time they could be built, and building
 * them anyway would make the one they stopped on arrive last.
 *
 * Eight rooms are held at a time, about 4.7 MB in all. Anything older is let
 * go. On a TV with 285 MB free that is a sensible amount of memory to spend on
 * the thing the whole screen is made of.
 */
public final class FieldCache {

    private static final int KEEP = 8;

    public interface Ready {
        /** Called on the main thread when a room asked for is ready. */
        void onField(String key, Bitmap field);
    }

    private final Map<String, Bitmap> rooms = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > KEEP;
        }
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private String wantedKey;
    private Field.Spec wantedSpec;
    private Ready callback;
    private Thread worker;
    private boolean stopped;

    public Bitmap get(String key) {
        synchronized (lock) {
            return rooms.get(key);
        }
    }

    /**
     * Ask for a room.
     *
     * If it is already built this returns it at once and nothing happens in the
     * background. Otherwise the callback arrives later, on the main thread, and
     * only if that room is still the one wanted.
     */
    public Bitmap request(String key, Field.Spec spec, Ready ready) {
        Bitmap have = get(key);
        if (have != null) {
            return have;
        }
        synchronized (lock) {
            wantedKey = key;
            wantedSpec = spec;
            callback = ready;
            lock.notifyAll();
            if (worker == null) {
                worker = new Thread(this::run, "atrium-fields");
                worker.setPriority(Thread.NORM_PRIORITY - 1);
                worker.start();
            }
        }
        return null;
    }

    /** Build rooms for a list of apps, quietly, while nothing else is asked for. */
    public void warm(String key, Field.Spec spec) {
        if (get(key) != null) {
            return;
        }
        Bitmap b = Field.build(spec);
        synchronized (lock) {
            rooms.put(key, b);
        }
    }

    private void run() {
        while (true) {
            String key;
            Field.Spec spec;
            Ready ready;
            synchronized (lock) {
                while (wantedKey == null && !stopped) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (stopped) {
                    return;
                }
                key = wantedKey;
                spec = wantedSpec;
                ready = callback;
                wantedKey = null;
                wantedSpec = null;
            }

            Bitmap made;
            try {
                made = Field.build(spec);
            } catch (OutOfMemoryError e) {
                // Losing the artwork is survivable. The screen keeps whatever
                // room it is already showing.
                synchronized (lock) {
                    rooms.clear();
                }
                continue;
            }

            final Bitmap result = made;
            final String doneKey = key;
            final Ready cb = ready;
            synchronized (lock) {
                rooms.put(doneKey, result);
            }
            if (cb != null) {
                main.post(() -> cb.onField(doneKey, result));
            }
        }
    }

    /** Drop everything, for instance when the time of day changes the light. */
    public void clear() {
        synchronized (lock) {
            rooms.clear();
        }
    }

    public void stop() {
        synchronized (lock) {
            stopped = true;
            lock.notifyAll();
        }
    }
}
