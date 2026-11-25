package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ContentRepositoryTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 100 * HOUR;
    private static final Set<String> NOTHING_INSTALLED = Collections.emptySet();
    private static final Set<Genre> NO_GENRES = EnumSet.noneOf(Genre.class);

    @Rule
    public final TemporaryFolder dir = new TemporaryFolder();

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final List<HomeFeed> delivered = Collections.synchronizedList(new ArrayList<>());

    @After
    public void stopPool() {
        pool.shutdownNow();
    }

    /** Answers trending and popular with one title each. Counts requests. */
    private static final class CountingTmdb implements Tmdb {
        final AtomicInteger requests = new AtomicInteger();
        volatile boolean offline;
        volatile boolean broken;
        volatile CountDownLatch gate;

        @Override
        public String get(String pathAndQuery) throws IOException {
            requests.incrementAndGet();
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            if (offline) {
                throw new IOException("offline");
            }
            if (broken) {
                throw new IllegalStateException("a bug, not a network error");
            }
            String path = pathAndQuery.split("\\?")[0];
            if (path.equals("/trending/all/week")) {
                return "{\"results\": [{\"id\": 1, \"media_type\": \"movie\", \"title\": \"New\", \"poster_path\": \"/p\"}]}";
            }
            if (path.equals("/tv/popular")) {
                return "{\"results\": [{\"id\": 2, \"name\": \"Show\", \"poster_path\": \"/p\"}]}";
            }
            throw new IOException("not found");
        }
    }

    private ContentRepository repo(CountingTmdb tmdb) {
        // Running on the calling thread keeps each test in order.
        return new ContentRepository(new FeedStore(dir.getRoot()), new FeedFetcher(tmdb, pool),
                Runnable::run, Runnable::run, () -> NOW);
    }

    private void saveFeed(long savedAt, String name) throws IOException {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        rows.put(HomeFeed.Row.TRENDING,
                Collections.singletonList(new Title(Title.Kind.MOVIE, 9, name, "", "/b", null)));
        new FeedStore(dir.getRoot()).save(new HomeFeed(savedAt, rows));
    }

    private String trendingName(HomeFeed f) {
        return f.rows.get(HomeFeed.Row.TRENDING).get(0).name;
    }

    @Test
    public void showsAFreshSavedFeedWithoutAskingTmdb() throws Exception {
        saveFeed(NOW - HOUR, "Saved");
        CountingTmdb tmdb = new CountingTmdb();

        repo(tmdb).load(NOTHING_INSTALLED, NO_GENRES, delivered::add);

        assertEquals(1, delivered.size());
        assertEquals("Saved", trendingName(delivered.get(0)));
        assertEquals(0, tmdb.requests.get());
    }

    @Test
    public void fetchesAgainWhenTheStreamingAppsOnTheTvChanged() throws Exception {
        saveFeed(NOW - HOUR, "Saved");        // fresh, but saved with no streaming apps
        CountingTmdb tmdb = new CountingTmdb();

        repo(tmdb).load(Collections.singleton("com.peacock.peacockfiretv"), NO_GENRES, delivered::add);

        assertEquals(2, delivered.size());
        assertTrue(tmdb.requests.get() > 0);
    }

    @Test
    public void fetchesAgainWhenTheChosenGenresChanged() throws Exception {
        saveFeed(NOW - HOUR, "Saved");        // fresh, but saved with no genres
        CountingTmdb tmdb = new CountingTmdb();

        repo(tmdb).load(NOTHING_INSTALLED, EnumSet.of(Genre.COMEDY), delivered::add);

        assertEquals(2, delivered.size());
        assertEquals(EnumSet.of(Genre.COMEDY), delivered.get(1).genres);
    }

    @Test
    public void showsAnOldSavedFeedThenTheNewOneAndSavesIt() throws Exception {
        saveFeed(NOW - 7 * HOUR, "Saved");

        repo(new CountingTmdb()).load(NOTHING_INSTALLED, NO_GENRES, delivered::add);

        assertEquals(2, delivered.size());
        assertEquals("Saved", trendingName(delivered.get(0)));
        assertEquals("New", trendingName(delivered.get(1)));
        HomeFeed onDisk = new FeedStore(dir.getRoot()).load();
        assertEquals(NOW, onDisk.savedAt);
        assertEquals("New", trendingName(onDisk));
    }

    @Test
    public void fetchesWhenNothingIsSaved() {
        repo(new CountingTmdb()).load(NOTHING_INSTALLED, NO_GENRES, delivered::add);

        assertEquals(1, delivered.size());
        assertEquals("New", trendingName(delivered.get(0)));
    }

    @Test
    public void keepsTheSavedFeedWhenOffline() throws Exception {
        saveFeed(NOW - 7 * HOUR, "Saved");
        CountingTmdb tmdb = new CountingTmdb();
        tmdb.offline = true;

        repo(tmdb).load(NOTHING_INSTALLED, NO_GENRES, delivered::add);

        assertEquals(1, delivered.size());
        assertEquals("Saved", trendingName(delivered.get(0)));
        assertEquals(NOW - 7 * HOUR, new FeedStore(dir.getRoot()).load().savedAt);
    }

    @Test
    public void showsNothingWhenOfflineWithNothingSaved() {
        CountingTmdb tmdb = new CountingTmdb();
        tmdb.offline = true;

        repo(tmdb).load(NOTHING_INSTALLED, NO_GENRES, delivered::add);

        assertTrue(delivered.isEmpty());
    }

    @Test
    public void aSecondLoadDuringAFetchDoesNotFetchAgain() throws Exception {
        CountingTmdb tmdb = new CountingTmdb();
        tmdb.gate = new CountDownLatch(1);
        ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            ContentRepository r = new ContentRepository(new FeedStore(dir.getRoot()),
                    new FeedFetcher(tmdb, pool), background, Runnable::run, () -> NOW);

            r.load(NOTHING_INSTALLED, NO_GENRES, delivered::add);
            r.load(NOTHING_INSTALLED, NO_GENRES, delivered::add);
            tmdb.gate.countDown();
            background.shutdown();
            assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));

            // One fetch is two list requests: trending and popular.
            assertEquals(2, tmdb.requests.get() - detailRequests(delivered));
            assertEquals(1, delivered.size());
        } finally {
            background.shutdownNow();
        }
    }

    @Test
    public void aLoadWithOtherGenresDuringAFetchRunsWhenItEnds() throws Exception {
        CountingTmdb tmdb = new CountingTmdb();
        tmdb.gate = new CountDownLatch(1);
        ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            ContentRepository r = new ContentRepository(new FeedStore(dir.getRoot()),
                    new FeedFetcher(tmdb, pool), background, Runnable::run, () -> NOW);

            r.load(NOTHING_INSTALLED, NO_GENRES, delivered::add);
            r.load(NOTHING_INSTALLED, EnumSet.of(Genre.COMEDY), delivered::add);
            tmdb.gate.countDown();
            background.shutdown();
            assertTrue(background.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(EnumSet.of(Genre.COMEDY), delivered.get(delivered.size() - 1).genres);
        } finally {
            background.shutdownNow();
        }
    }

    @Test
    public void aLoadThatFailsWithAnErrorDoesNotBlockTheNext() {
        CountingTmdb tmdb = new CountingTmdb();
        tmdb.broken = true;
        ContentRepository r = repo(tmdb);
        try {
            r.load(NOTHING_INSTALLED, NO_GENRES, delivered::add);
        } catch (IllegalStateException expected) {
            // The background executor runs on this thread, so the error lands here.
        }
        tmdb.broken = false;

        r.load(NOTHING_INSTALLED, NO_GENRES, delivered::add);

        assertEquals("New", trendingName(delivered.get(delivered.size() - 1)));
    }

    /** One detail request per title in the delivered feed. */
    private static int detailRequests(List<HomeFeed> feeds) {
        int n = 0;
        for (HomeFeed f : feeds) {
            for (List<Title> row : f.rows.values()) {
                n += row.size();
            }
        }
        return n;
    }
}
