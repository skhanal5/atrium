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
    static final int MIN_VOTES = 200;

    private final Tmdb tmdb;
    private final ExecutorService pool;

    /** @param pool runs the detail requests side by side. The caller owns it. */
    public FeedFetcher(Tmdb tmdb, ExecutorService pool) {
        this.tmdb = tmdb;
        this.pool = pool;
    }

    /**
     * @param installed package names of the apps on this TV
     * @param genres    the genres for Your genres. None leaves the row out.
     * @param now       stored as the feed's fetch time
     */
    public HomeFeed fetch(Set<String> installed, Set<Genre> genres, long now) throws IOException {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        put(rows, HomeFeed.Row.TRENDING, list("/trending/all/week?" + LANGUAGE, null));
        Set<Integer> services = StreamingApps.includedServices(installed);
        if (!services.isEmpty()) {
            put(rows, HomeFeed.Row.ON_YOUR_APPS, onServices(services));
        }
        // With no genres chosen, nothing is asked and the row is left out.
        put(rows, HomeFeed.Row.YOUR_GENRES, inGenres(genres));
        put(rows, HomeFeed.Row.POPULAR_SHOWS, list("/tv/popular?" + LANGUAGE, Title.Kind.TV));
        if (rows.isEmpty()) {
            throw new IOException("TMDB returned no rows");
        }
        return new HomeFeed(now, withDetails(rows), services, genres);
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

    /** Popular movies and shows on these services, taken in turns. */
    private List<Title> onServices(Set<Integer> services) {
        String query = "?" + LANGUAGE + "&watch_region=" + COUNTRY
                + "&with_watch_providers=" + anyOf(services) + "&sort_by=popularity.desc";
        return inTurns(list("/discover/movie" + query, Title.Kind.MOVIE),
                list("/discover/tv" + query, Title.Kind.TV));
    }

    /** Popular movies and shows in any of these genres, taken in turns. */
    private List<Title> inGenres(Set<Genre> genres) {
        // Without a floor, the most popular movies include titles only a handful
        // of people have rated, some with no picture. Checked against TMDB.
        String query = "?" + LANGUAGE + "&sort_by=popularity.desc&vote_count.gte=" + MIN_VOTES + "&with_genres=";
        Set<Integer> movieIds = Genre.movieIds(genres);
        Set<Integer> showIds = Genre.showIds(genres);
        return inTurns(
                movieIds.isEmpty() ? null : list("/discover/movie" + query + anyOf(movieIds), Title.Kind.MOVIE),
                showIds.isEmpty() ? null : list("/discover/tv" + query + anyOf(showIds), Title.Kind.TV));
    }

    /** "386|387", encoded for the URL: TMDB's way to ask for any of these ids. */
    private static String anyOf(Set<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : new TreeSet<>(ids)) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(id);
        }
        return Text.urlEncode(sb.toString());
    }

    /** One movie, then one show, so neither fills the row. Null when both lists are missing. */
    private static List<Title> inTurns(List<Title> movies, List<Title> shows) {
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
