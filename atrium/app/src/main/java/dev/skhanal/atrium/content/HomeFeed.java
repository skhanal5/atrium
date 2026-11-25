package dev.skhanal.atrium.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The rows of titles on the home screen, and when they were fetched.
 *
 * It is saved to disk as JSON after every fetch. The next start draws from
 * the saved copy at once, and the TV still has rows to show when it is offline.
 */
public final class HomeFeed {

    /** Rows in the order they are drawn. */
    public enum Row { TRENDING, ON_YOUR_APPS, YOUR_GENRES, POPULAR_SHOWS }

    /** After this long the feed is fetched again. TMDB trending changes daily. */
    static final long FRESH_FOR_MS = 6 * 3_600_000L;

    /** When the feed was fetched, in milliseconds since 1970. */
    public final long savedAt;
    public final Map<Row, List<Title>> rows;
    /**
     * The TMDB services "On your apps" was fetched for. When the streaming
     * apps on the TV change, the feed no longer matches and is fetched again.
     */
    public final Set<Integer> services;
    /** The genres "Your genres" was fetched for. A new choice fetches again. */
    public final Set<Genre> genres;

    public HomeFeed(long savedAt, Map<Row, List<Title>> rows) {
        this(savedAt, rows, Collections.<Integer>emptySet());
    }

    public HomeFeed(long savedAt, Map<Row, List<Title>> rows, Set<Integer> services) {
        this(savedAt, rows, services, Collections.<Genre>emptySet());
    }

    public HomeFeed(long savedAt, Map<Row, List<Title>> rows, Set<Integer> services, Set<Genre> genres) {
        this.savedAt = savedAt;
        this.rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        this.services = Collections.unmodifiableSet(new HashSet<>(services));
        Set<Genre> g = EnumSet.noneOf(Genre.class);
        g.addAll(genres);
        this.genres = Collections.unmodifiableSet(g);
    }

    /** True when the feed is recent enough that fetching again is a waste. */
    public boolean isFresh(long now) {
        // A feed from the future means the clock moved back, so trust nothing.
        return now >= savedAt && now - savedAt < FRESH_FOR_MS;
    }

    /** The title with this id in any row, or null. */
    public Title find(String id) {
        for (List<Title> row : rows.values()) {
            for (Title t : row) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
        }
        return null;
    }

    // ---- Saved form -----------------------------------------------------

    public String toJson() {
        try {
            JSONArray rowsJson = new JSONArray();
            for (Map.Entry<Row, List<Title>> e : rows.entrySet()) {
                JSONArray titles = new JSONArray();
                for (Title t : e.getValue()) {
                    titles.put(write(t));
                }
                rowsJson.put(new JSONObject().put("row", e.getKey().name()).put("titles", titles));
            }
            return new JSONObject().put("savedAt", savedAt).put("rows", rowsJson)
                    .put("services", new JSONArray(services)).put("genres", new JSONArray(Genre.names(genres)))
                    .toString();
        } catch (JSONException e) {
            // Only a NaN number can cause this, and no field here holds one.
            throw new IllegalStateException(e);
        }
    }

    /** Read a saved feed. Returns null when the text is missing or damaged. */
    public static HomeFeed fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(json);
            Map<Row, List<Title>> rows = new LinkedHashMap<>();
            JSONArray rowsJson = o.getJSONArray("rows");
            for (int i = 0; i < rowsJson.length(); i++) {
                JSONObject r = rowsJson.getJSONObject(i);
                Row row = rowNamed(r.getString("row"));
                if (row == null) {
                    continue;
                }
                List<Title> titles = new ArrayList<>();
                JSONArray ts = r.getJSONArray("titles");
                for (int j = 0; j < ts.length(); j++) {
                    titles.add(read(ts.getJSONObject(j)));
                }
                rows.put(row, titles);
            }
            Set<Integer> services = new HashSet<>();
            JSONArray s = o.optJSONArray("services");
            for (int i = 0; s != null && i < s.length(); i++) {
                services.add(s.getInt(i));
            }
            List<String> genres = new ArrayList<>();
            JSONArray g = o.optJSONArray("genres");
            for (int i = 0; g != null && i < g.length(); i++) {
                genres.add(g.getString(i));
            }
            return new HomeFeed(o.getLong("savedAt"), rows, services, Genre.fromNames(genres));
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Row rowNamed(String name) {
        for (Row r : Row.values()) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        return null;
    }

    private static JSONObject write(Title t) throws JSONException {
        JSONObject o = new JSONObject()
                .put("kind", t.kind.path)
                .put("id", t.tmdbId)
                .put("name", t.name)
                .put("year", t.year)
                .putOpt("backdrop", t.backdrop)
                .putOpt("poster", t.poster);
        Details d = t.details;
        if (d != null) {
            JSONArray offers = new JSONArray();
            for (Offer of : d.offers) {
                offers.put(new JSONObject().put("id", of.providerId).put("provider", of.provider)
                        .put("mode", of.mode.name()).putOpt("logo", of.logo));
            }
            o.put("details", new JSONObject()
                    .put("rating", d.rating)
                    .put("score", d.score)
                    .put("length", d.length)
                    .put("genres", new JSONArray(d.genres))
                    .put("overview", d.overview)
                    .put("tagline", d.tagline)
                    .putOpt("trailer", d.trailer)
                    .put("offers", offers));
        }
        return o;
    }

    private static Title read(JSONObject o) throws JSONException {
        Title.Kind kind = Title.Kind.from(o.getString("kind"));
        if (kind == null) {
            throw new JSONException("unknown kind " + o.getString("kind"));
        }
        Details details = null;
        JSONObject d = o.optJSONObject("details");
        if (d != null) {
            List<String> genres = new ArrayList<>();
            JSONArray g = d.getJSONArray("genres");
            for (int i = 0; i < g.length(); i++) {
                genres.add(g.getString(i));
            }
            List<Offer> offers = new ArrayList<>();
            JSONArray of = d.getJSONArray("offers");
            for (int i = 0; i < of.length(); i++) {
                JSONObject x = of.getJSONObject(i);
                offers.add(new Offer(x.getInt("id"), x.getString("provider"),
                        Offer.Mode.valueOf(x.getString("mode")), optText(x, "logo")));
            }
            details = new Details(d.getString("rating"), d.getDouble("score"), d.getString("length"),
                    genres, d.getString("overview"), d.getString("tagline"), optText(d, "trailer"), offers);
        }
        return new Title(kind, o.getInt("id"), o.getString("name"), o.getString("year"),
                optText(o, "backdrop"), optText(o, "poster"), details);
    }

    /** A text field that may be missing, as null rather than "". */
    private static String optText(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optString(key) : null;
    }
}
