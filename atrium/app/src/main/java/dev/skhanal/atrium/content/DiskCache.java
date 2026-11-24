package dev.skhanal.atrium.content;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Downloaded images, kept on disk so the next start does not fetch them again.
 *
 * Each file is named by a hash of its address. When the folder grows past its
 * limit, the files used longest ago are removed first. A file's modified time
 * is its last use, so no index file is needed and nothing can get out of step.
 */
public final class DiskCache {

    private final File dir;
    private final long maxBytes;

    public DiskCache(File dir, long maxBytes) {
        this.dir = dir;
        this.maxBytes = maxBytes;
    }

    /** The cached file for this address, or null. Marks it as just used. */
    public synchronized File get(String url) {
        File f = new File(dir, name(url));
        if (!f.isFile()) {
            return null;
        }
        f.setLastModified(System.currentTimeMillis());
        return f;
    }

    /** Save bytes for this address, then trim the folder to its limit. */
    public synchronized void put(String url, byte[] data) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        File target = new File(dir, name(url));
        File tmp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("cannot save " + target.getName());
        }
        trim();
    }

    private void trim() {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        if (total <= maxBytes) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            if (total <= maxBytes) {
                break;
            }
            long size = f.length();
            if (f.delete()) {
                total -= size;
            }
        }
    }

    private static String name(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is always available", e);
        }
    }
}
