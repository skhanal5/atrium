package dev.skhanal.atrium.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the last home feed in one file.
 *
 * It uses java.io only. java.nio.file needs Android 8, and this TV runs 7.1.
 */
public final class FeedStore {

    static final String FILE = "home-feed.json";

    private final File dir;

    public FeedStore(File dir) {
        this.dir = dir;
    }

    /** The saved feed, or null when there is none or the file is damaged. */
    public HomeFeed load() {
        File f = new File(dir, FILE);
        if (!f.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(f)) {
            return HomeFeed.fromJson(Text.read(in));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Write to a temporary file, then rename it over the old one. A rename
     * on one disk is all or nothing, so losing power mid-save keeps the old
     * feed whole instead of leaving half a file.
     */
    public void save(HomeFeed feed) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        File tmp = new File(dir, FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(feed.toJson().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(new File(dir, FILE))) {
            tmp.delete();
            throw new IOException("cannot replace " + FILE);
        }
    }
}
