package dev.skhanal.atrium.content;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FeedStoreTest {

    @Rule
    public final TemporaryFolder dir = new TemporaryFolder();

    private static HomeFeed feed(long savedAt, String name) {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        rows.put(HomeFeed.Row.TRENDING,
                Collections.singletonList(new Title(Title.Kind.MOVIE, 1, name, "2024", "/b.jpg", null)));
        return new HomeFeed(savedAt, rows);
    }

    @Test
    public void loadsWhatWasSaved() throws Exception {
        FeedStore store = new FeedStore(dir.getRoot());

        store.save(feed(7L, "Dune"));
        HomeFeed loaded = store.load();

        assertEquals(7L, loaded.savedAt);
        assertEquals("Dune", loaded.find("movie-1").name);
    }

    @Test
    public void aNewSaveReplacesTheOldOneAndLeavesNoTemporaryFile() throws Exception {
        FeedStore store = new FeedStore(dir.getRoot());

        store.save(feed(1L, "Old"));
        store.save(feed(2L, "New"));

        assertEquals("New", store.load().find("movie-1").name);
        assertArrayEquals(new String[] {FeedStore.FILE}, dir.getRoot().list());
    }

    @Test
    public void loadsNothingBeforeTheFirstSave() {
        assertNull(new FeedStore(dir.getRoot()).load());
    }

    @Test
    public void loadsNothingFromADamagedFile() throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(dir.getRoot(), FeedStore.FILE))) {
            out.write("{\"savedAt\": 1, \"ro".getBytes(StandardCharsets.UTF_8));
        }

        assertNull(new FeedStore(dir.getRoot()).load());
    }

    @Test
    public void createsTheFolderWhenItIsMissing() throws Exception {
        File missing = new File(dir.getRoot(), "content");
        FeedStore store = new FeedStore(missing);

        store.save(feed(3L, "Here"));

        assertEquals(3L, store.load().savedAt);
    }
}
