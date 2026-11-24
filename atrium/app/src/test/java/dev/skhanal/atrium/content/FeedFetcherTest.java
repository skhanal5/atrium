package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Test;

public class FeedFetcherTest {

    private static final Set<String> PEACOCK = Collections.singleton("com.peacock.peacockfiretv");

    /** Answers by path, without the query. Records every request. */
    private static final class FakeTmdb implements Tmdb {
        final Map<String, String> answers = new HashMap<>();
        final Set<String> failing = new HashSet<>();
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String get(String pathAndQuery) throws IOException {
            asked.add(pathAndQuery);
            String path = pathAndQuery.split("\\?")[0];
            if (failing.contains(path)) {
                throw new IOException("offline");
            }
            String a = answers.get(path);
            if (a == null) {
                throw new IOException("404 " + path);
            }
            return a;
        }

        FakeTmdb list(String path, String kind, int... ids) {
            StringBuilder sb = new StringBuilder("{\"results\": [");
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"id\": ").append(ids[i]);
                if (kind != null) {
                    sb.append(", \"media_type\": \"").append(kind).append('"');
                }
                sb.append(", \"title\": \"T").append(ids[i]).append("\", \"poster_path\": \"/p.jpg\"}");
            }
            answers.put(path, sb.append("]}").toString());
            return this;
        }

        FakeTmdb details(String kind, int id) {
            answers.put("/" + kind + "/" + id, "{\"id\": " + id + ", \"title\": \"T" + id
                    + "\", \"poster_path\": \"/p.jpg\", \"overview\": \"About " + id + "\"}");
            return this;
        }
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @After
    public void stopPool() {
        pool.shutdownNow();
    }

    private static FakeTmdb everyRowWorks() {
        return new FakeTmdb()
                .list("/trending/all/week", "movie", 1, 2)
                .list("/discover/movie", null, 3)
                .list("/discover/tv", null, 4)
                .list("/tv/popular", null, 5)
                .details("movie", 1).details("movie", 2).details("movie", 3)
                .details("tv", 4).details("tv", 5);
    }

    private static List<String> ids(List<Title> titles) {
        List<String> out = new ArrayList<>();
        for (Title t : titles) {
            out.add(t.id);
        }
        return out;
    }

    @Test
    public void fetchesThreeRowsInOrderWithDetails() throws Exception {
        HomeFeed feed = new FeedFetcher(everyRowWorks(), pool).fetch(PEACOCK, 42L);

        assertEquals(42L, feed.savedAt);
        assertEquals(Arrays.asList(HomeFeed.Row.TRENDING, HomeFeed.Row.ON_YOUR_APPS, HomeFeed.Row.POPULAR_SHOWS),
                new ArrayList<>(feed.rows.keySet()));
        assertEquals(Arrays.asList("movie-1", "movie-2"), ids(feed.rows.get(HomeFeed.Row.TRENDING)));
        assertEquals(Arrays.asList("movie-3", "tv-4"), ids(feed.rows.get(HomeFeed.Row.ON_YOUR_APPS)));
        assertEquals(Arrays.asList("tv-5"), ids(feed.rows.get(HomeFeed.Row.POPULAR_SHOWS)));
        assertEquals("About 4", feed.find("tv-4").details.overview);
    }

    @Test
    public void recordsTheServicesTheFeedWasFetchedFor() throws Exception {
        HomeFeed feed = new FeedFetcher(everyRowWorks(), pool).fetch(PEACOCK, 0);

        assertEquals(new HashSet<>(Arrays.asList(386, 387)), feed.services);
    }

    @Test
    public void asksForTitlesOnTheInstalledServicesInTheUs() throws Exception {
        FakeTmdb tmdb = everyRowWorks();

        new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);

        String discover = find(tmdb.asked, "/discover/movie?");
        assertTrue(discover, discover.contains("watch_region=US"));
        assertTrue(discover, discover.contains("with_watch_providers=386%7C387")
                || discover.contains("with_watch_providers=387%7C386"));
        String details = find(tmdb.asked, "/movie/1?");
        assertTrue(details, details.contains("append_to_response=videos%2Cwatch%2Fproviders%2Crelease_dates"));
        assertTrue(find(tmdb.asked, "/tv/4?").contains("content_ratings"));
    }

    @Test
    public void mixesMoviesAndShowsOnYourApps() throws Exception {
        FakeTmdb tmdb = everyRowWorks().list("/discover/movie", null, 3, 6).list("/discover/tv", null, 4, 7)
                .details("movie", 6).details("tv", 7);

        HomeFeed feed = new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);

        assertEquals(Arrays.asList("movie-3", "tv-4", "movie-6", "tv-7"),
                ids(feed.rows.get(HomeFeed.Row.ON_YOUR_APPS)));
    }

    @Test
    public void leavesOutYourAppsWhenNoStreamingAppIsInstalled() throws Exception {
        FakeTmdb tmdb = everyRowWorks();

        HomeFeed feed = new FeedFetcher(tmdb, pool).fetch(Collections.<String>emptySet(), 0);

        assertFalse(feed.rows.containsKey(HomeFeed.Row.ON_YOUR_APPS));
        assertNull(findOrNull(tmdb.asked, "/discover/"));
    }

    @Test
    public void keepsATitleWithoutDetailsWhenItsDetailsFail() throws Exception {
        FakeTmdb tmdb = everyRowWorks();
        tmdb.failing.add("/movie/2");

        HomeFeed feed = new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);

        assertNull(feed.find("movie-2").details);
        assertNotNull(feed.find("movie-1").details);
    }

    @Test
    public void leavesOutARowThatFails() throws Exception {
        FakeTmdb tmdb = everyRowWorks();
        tmdb.failing.add("/tv/popular");

        HomeFeed feed = new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);

        assertFalse(feed.rows.containsKey(HomeFeed.Row.POPULAR_SHOWS));
        assertTrue(feed.rows.containsKey(HomeFeed.Row.TRENDING));
    }

    @Test
    public void failsWhenEveryRowFails() {
        FakeTmdb tmdb = everyRowWorks();
        tmdb.failing.addAll(Arrays.asList("/trending/all/week", "/discover/movie", "/discover/tv", "/tv/popular"));

        try {
            new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);
            fail("expected IOException");
        } catch (IOException expected) {
            // The caller keeps the saved feed.
        }
    }

    @Test
    public void keepsAtMostTwelveTitlesPerRow() throws Exception {
        int[] many = new int[20];
        FakeTmdb tmdb = everyRowWorks();
        for (int i = 0; i < many.length; i++) {
            many[i] = 100 + i;
            tmdb.details("movie", 100 + i);
        }
        tmdb.list("/trending/all/week", "movie", many);

        HomeFeed feed = new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);

        assertEquals(12, feed.rows.get(HomeFeed.Row.TRENDING).size());
        assertNull(findOrNull(tmdb.asked, "/movie/112?"));
    }

    @Test
    public void asksForDetailsOnceForATitleInTwoRows() throws Exception {
        FakeTmdb tmdb = everyRowWorks().list("/discover/movie", null, 1);

        new FeedFetcher(tmdb, pool).fetch(PEACOCK, 0);

        int count = 0;
        for (String a : tmdb.asked) {
            if (a.startsWith("/movie/1?")) {
                count++;
            }
        }
        assertEquals(1, count);
    }

    private static String find(List<String> asked, String prefix) {
        String a = findOrNull(asked, prefix);
        assertNotNull("no request starting " + prefix + " in " + asked, a);
        return a;
    }

    private static String findOrNull(List<String> asked, String prefix) {
        synchronized (asked) {
            for (String a : asked) {
                if (a.startsWith(prefix)) {
                    return a;
                }
            }
        }
        return null;
    }
}
