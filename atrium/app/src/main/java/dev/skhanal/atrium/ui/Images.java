package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.skhanal.atrium.content.DiskCache;
import dev.skhanal.atrium.content.Http;
import dev.skhanal.atrium.content.Text;
import dev.skhanal.atrium.content.TmdbImages;

/**
 * Loads TMDB artwork for the screen.
 *
 * Three things keep it cheap on a TV with about 285 MB free:
 *
 * - Images are decoded shrunk to the size they are drawn, never larger.
 * - Opaque artwork is decoded as RGB_565, half the memory of full colour.
 *   Movie stills hide its smaller range well.
 * - Memory holds at most {@link #MEMORY_BYTES}. The disk holds the rest, so a
 *   restart redraws without the network.
 *
 * Two requests for the same image share one download.
 */
public final class Images {

    /** Turns a decoded image into the one drawn, on a background thread. */
    public interface Transform {
        /** Part of the cache key, so two transforms of one image are kept apart. */
        String key();

        Bitmap apply(Bitmap decoded);
    }

    public interface Callback {
        /** On the main thread. Not called when the image could not be loaded. */
        void onImage(String key, Bitmap bitmap);
    }

    /** About six hero pictures, or seventy cards. Measured on the TV, 24 MB was too much. */
    private static final int MEMORY_BYTES = 12 * 1024 * 1024;
    private static final long DISK_BYTES = 48L * 1024 * 1024;
    private static final int TIMEOUT_MS = 15_000;

    private static Images instance;

    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(MEMORY_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Map<String, List<Callback>> waiting = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DiskCache disk;
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "atrium-images");
        t.setDaemon(true);
        return t;
    });

    private Images(Context context) {
        disk = new DiskCache(new File(context.getCacheDir(), "images"), DISK_BYTES);
    }

    public static synchronized Images get(Context context) {
        if (instance == null) {
            instance = new Images(context.getApplicationContext());
        }
        return instance;
    }

    /** The key a request is cached and called back under. */
    public static String key(String url, int width, int height, Transform transform) {
        return url + "@" + width + "x" + height + (transform == null ? "" : "#" + transform.key());
    }

    /**
     * Load one image at a drawn size.
     *
     * @return the bitmap at once when it is in memory, else null and the
     *         callback runs later
     */
    public Bitmap load(String url, int width, int height, boolean opaque, Transform transform,
            Callback callback) {
        if (url == null) {
            return null;
        }
        String key = key(url, width, height, transform);
        Bitmap have = memory.get(key);
        if (have != null) {
            return have;
        }
        List<Callback> list = waiting.get(key);
        if (list != null) {
            list.add(callback);
            return null;
        }
        list = new ArrayList<>();
        list.add(callback);
        waiting.put(key, list);
        pool.execute(() -> {
            Bitmap bmp = fetch(url, width, height, opaque);
            if (bmp != null && transform != null) {
                Bitmap out = transform.apply(bmp);
                if (out != bmp) {
                    bmp.recycle();
                }
                bmp = out;
            }
            Bitmap done = bmp;
            main.post(() -> {
                List<Callback> callbacks = waiting.remove(key);
                if (done == null) {
                    return;
                }
                memory.put(key, done);
                for (Callback c : callbacks) {
                    c.onImage(key, done);
                }
            });
        });
        return null;
    }

    /** From disk, else from the network and then saved to disk. Null on failure. */
    private Bitmap fetch(String url, int width, int height, boolean opaque) {
        try {
            byte[] data;
            File cached = disk.get(url);
            if (cached != null) {
                data = readFile(cached);
            } else {
                data = Http.get(url, null, TIMEOUT_MS);
                disk.put(url, data);
            }
            return decode(data, width, height, opaque);
        } catch (IOException | RuntimeException e) {
            // A missing picture leaves a plain card. It is tried again next time.
            return null;
        }
    }

    private static Bitmap decode(byte[] data, int width, int height, boolean opaque) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o.inJustDecodeBounds = false;
        o.inSampleSize = TmdbImages.sampleSize(o.outWidth, o.outHeight, width, height);
        o.inPreferredConfig = opaque ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private static byte[] readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return Text.readBytes(in);
        }
    }
}
