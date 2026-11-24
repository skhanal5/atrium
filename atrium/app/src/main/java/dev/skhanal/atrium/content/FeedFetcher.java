package dev.skhanal.atrium.content;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.json.JSONException;

/**
 * Fetches the home feed from TMDB: the three rows, then the details of every
 * title in them.
 *
 * It is built to degrade, not to fail. A title whose details fail is kept
 * without them. A row that fails is left out. Only when every row fails does
 * the fetch fail, and then the caller keeps the feed it saved last time.
 */
public final class FeedFetcher {

    /** Where-to-watch data and age ratings are for this country. */
    public static final String COUNTRY = "US";
    static final int ROW_SIZE = 12;
    private static final String LANGUAGE = "language=en-US";

    private final Tmdb tmdb;
    private final ExecutorService pool;

    /** @param pool runs the detail requests side by side. The caller owns it. */
    public FeedFetcher(Tmdb tmdb, ExecutorService pool) {
        this.tmdb = tmdb;
        this.pool = pool;
    }

    /**
     * @param installed package names of the apps on this TV
     * @param now       stored as the feed's fetch time
     */
    public HomeFeed fetch(Set<String> installed, long now) throws IOException {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        put(rows, HomeFeed.Row.TRENDING, list("/trending/all/week?" + LANGUAGE, null));
        Set<Integer> services = StreamingApps.includedServices(installed);
        if (!services.isEmpty()) {
            put(rows, HomeFeed.Row.ON_YOUR_APPS, onServices(services));
        }
        put(rows, HomeFeed.Row.POPULAR_SHOWS, list("/tv/popular?" + LANGUAGE, Title.Kind.TV));
        if (rows.isEmpty()) {
            throw new IOException("TMDB returned no rows");
        }
        return new HomeFeed(now, withDetails(rows), services);
    }

    private static void put(Map<HomeFeed.Row, List<Title>> rows, HomeFeed.Row row, List<Title> titles) {
        if (titles != null) {
            rows.put(row, titles.size() > ROW_SIZE ? titles.subList(0, ROW_SIZE) : titles);
        }
    }

    /** One list, or null when it fails. */
    private List<Title> list(String pathAndQuery, Title.Kind kind) {
        try {
            return TmdbParser.list(tmdb.get(pathAndQuery), kind);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Popular movies and shows on these services, taken in turns so neither fills the row. */
    private List<Title> onServices(Set<Integer> services) {
        StringBuilder ids = new StringBuilder();
        for (int id : new TreeSet<>(services)) {
            if (ids.length() > 0) {
                ids.append('|');
            }
            ids.append(id);
        }
        String query = "?" + LANGUAGE + "&watch_region=" + COUNTRY
                + "&with_watch_providers=" + Text.urlEncode(ids.toString()) + "&sort_by=popularity.desc";
        List<Title> movies = list("/discover/movie" + query, Title.Kind.MOVIE);
        List<Title> shows = list("/discover/tv" + query, Title.Kind.TV);
        if (movies == null && shows == null) {
            return null;
        }
        List<Title> out = new ArrayList<>();
        int m = movies == null ? 0 : movies.size();
        int s = shows == null ? 0 : shows.size();
        for (int i = 0; i < Math.max(m, s); i++) {
            if (i < m) {
                out.add(movies.get(i));
            }
            if (i < s) {
                out.add(shows.get(i));
            }
        }
        return out;
    }

    /** Fetch details for every title once, side by side, and put them in place. */
    private Map<HomeFeed.Row, List<Title>> withDetails(Map<HomeFeed.Row, List<Title>> rows)
            throws IOException {
        Map<String, Future<Title>> pending = new LinkedHashMap<>();
        for (List<Title> row : rows.values()) {
            for (Title t : row) {
                if (!pending.containsKey(t.id)) {
                    pending.put(t.id, pool.submit(() -> details(t)));
                }
            }
        }
        Map<HomeFeed.Row, List<Title>> out = new LinkedHashMap<>();
        for (Map.Entry<HomeFeed.Row, List<Title>> e : rows.entrySet()) {
            List<Title> filled = new ArrayList<>();
            for (Title t : e.getValue()) {
                Title full = await(pending.get(t.id));
                filled.add(full != null ? full : t);
            }
            out.put(e.getKey(), filled);
        }
        return out;
    }

    /** The finished title, or null when its details failed. */
    private static Title await(Future<Title> f) throws IOException {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("fetch stopped");
        } catch (ExecutionException e) {
            // details() catches its own errors. This is only a safety net.
            return null;
        }
    }

    /** The title with details, or null when they could not be fetched. */
    private Title details(Title t) {
        String append = t.kind == Title.Kind.MOVIE
                ? "videos,watch/providers,release_dates"
                : "videos,watch/providers,content_ratings";
        try {
            String json = tmdb.get("/" + t.kind.path + "/" + t.tmdbId + "?" + LANGUAGE
                    + "&append_to_response=" + Text.urlEncode(append));
            return TmdbParser.details(json, t.kind, COUNTRY);
        } catch (IOException | JSONException e) {
            return null;
        }
    }
}
