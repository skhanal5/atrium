package dev.skhanal.atrium.content;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiskCacheTest {

    @Rule
    public final TemporaryFolder dir = new TemporaryFolder();

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) i;
        }
        return b;
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            for (int n; off < b.length && (n = in.read(b, off, b.length - off)) > 0; ) {
                off += n;
            }
            return b;
        }
    }

    @Test
    public void returnsWhatWasPut() throws Exception {
        DiskCache cache = new DiskCache(dir.getRoot(), 1000);

        cache.put("https://image.tmdb.org/t/p/w300/a.jpg", bytes(10));
        File f = cache.get("https://image.tmdb.org/t/p/w300/a.jpg");

        assertNotNull(f);
        assertArrayEquals(bytes(10), read(f));
    }

    @Test
    public void returnsNothingForAnAddressNeverPut() {
        assertNull(new DiskCache(dir.getRoot(), 1000).get("https://image.tmdb.org/t/p/w300/none.jpg"));
    }

    @Test
    public void keepsDifferentSizesOfOneImageApart() throws Exception {
        DiskCache cache = new DiskCache(dir.getRoot(), 1000);

        cache.put("https://image.tmdb.org/t/p/w300/a.jpg", bytes(3));
        cache.put("https://image.tmdb.org/t/p/w780/a.jpg", bytes(7));

        assertEquals(3, cache.get("https://image.tmdb.org/t/p/w300/a.jpg").length());
        assertEquals(7, cache.get("https://image.tmdb.org/t/p/w780/a.jpg").length());
    }

    @Test
    public void removesTheLeastRecentlyUsedFilesWhenOverTheLimit() throws Exception {
        DiskCache cache = new DiskCache(dir.getRoot(), 250);
        cache.put("u1", bytes(100));
        cache.put("u2", bytes(100));
        age(cache, "u1", 3_000);
        age(cache, "u2", 2_000);
        cache.get("u1");                    // u1 is used again, so u2 is now the oldest

        cache.put("u3", bytes(100));        // 300 bytes, over the 250 limit

        assertNotNull(cache.get("u1"));
        assertNull(cache.get("u2"));
        assertNotNull(cache.get("u3"));
    }

    @Test
    public void leavesNoTemporaryFilesBehind() throws Exception {
        DiskCache cache = new DiskCache(dir.getRoot(), 1000);

        cache.put("u1", bytes(5));
        cache.put("u1", bytes(6));

        String[] names = dir.getRoot().list();
        assertEquals(1, names.length);
        assertTrue(names[0], !names[0].endsWith(".tmp"));
    }

    @Test
    public void createsItsFolderWhenMissing() throws Exception {
        DiskCache cache = new DiskCache(new File(dir.getRoot(), "images"), 1000);

        cache.put("u1", bytes(5));

        assertNotNull(cache.get("u1"));
    }

    /** Make a cached file look older than it is. */
    private static void age(DiskCache cache, String url, long ms) {
        File f = cache.get(url);
        assertTrue(f.setLastModified(System.currentTimeMillis() - ms));
    }
}
