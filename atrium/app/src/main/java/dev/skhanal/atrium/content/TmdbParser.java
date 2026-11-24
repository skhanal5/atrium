package dev.skhanal.atrium.content;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Turns TMDB responses into titles. It does no network work. */
public final class TmdbParser {

    private TmdbParser() {
    }

    /**
     * Read a list response, such as trending or popular.
     *
     * @param kind the kind of every item, for lists that do not say. Null for
     *             lists that mix movies and shows and give a media_type.
     */
    public static List<Title> list(String json, Title.Kind kind) throws JSONException {
        JSONArray results = new JSONObject(json).optJSONArray("results");
        List<Title> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.getJSONObject(i);
            Title.Kind k = kind != null ? kind : Title.Kind.from(o.optString("media_type"));
            // A trending list also holds people. They have no place on the home screen.
            if (k == null) {
                continue;
            }
            String backdrop = path(o, "backdrop_path");
            String poster = path(o, "poster_path");
            // A title with no picture would draw as an empty card.
            if (backdrop == null && poster == null) {
                continue;
            }
            out.add(new Title(k, o.getInt("id"), name(o), year(o), backdrop, poster));
        }
        return out;
    }

    /**
     * Read one title's details. The request must append videos and either
     * release_dates (movies) or content_ratings (shows).
     *
     * @param country the ISO country code for the age rating, such as "US"
     */
    public static Title details(String json, Title.Kind kind, String country) throws JSONException {
        JSONObject o = new JSONObject(json);
        Details d = new Details(
                rating(o, kind, country),
                Math.round(o.optDouble("vote_average", 0) * 10) / 10.0,
                length(o, kind),
                genres(o),
                o.optString("overview"),
                o.optString("tagline"),
                trailer(o),
                offers(o, country));
        return new Title(kind, o.getInt("id"), name(o), year(o),
                path(o, "backdrop_path"), path(o, "poster_path"), d);
    }

    private static String rating(JSONObject o, Title.Kind kind, String country) {
        JSONArray countries = kind == Title.Kind.MOVIE
                ? arrayAt(o, "release_dates")
                : arrayAt(o, "content_ratings");
        for (int i = 0; i < countries.length(); i++) {
            JSONObject c = countries.optJSONObject(i);
            if (c == null || !country.equals(c.optString("iso_3166_1"))) {
                continue;
            }
            if (kind == Title.Kind.TV) {
                return c.optString("rating");
            }
            // A movie has one entry per release, such as cinema and digital.
            // Many of them leave the rating blank, so take the first one set.
            JSONArray releases = c.optJSONArray("release_dates");
            for (int j = 0; releases != null && j < releases.length(); j++) {
                String cert = releases.optJSONObject(j).optString("certification");
                if (!cert.isEmpty()) {
                    return cert;
                }
            }
        }
        return "";
    }

    private static String length(JSONObject o, Title.Kind kind) {
        if (kind == Title.Kind.TV) {
            int seasons = o.optInt("number_of_seasons", 0);
            if (seasons <= 0) {
                return "";
            }
            return seasons + (seasons == 1 ? " season" : " seasons");
        }
        int minutes = o.optInt("runtime", 0);
        if (minutes <= 0) {
            return "";
        }
        return minutes < 60 ? minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static List<String> genres(JSONObject o) {
        JSONArray all = o.optJSONArray("genres");
        List<String> out = new ArrayList<>();
        for (int i = 0; all != null && i < all.length() && out.size() < 3; i++) {
            out.add(all.optJSONObject(i).optString("name"));
        }
        return out;
    }

    /** The first official YouTube trailer, else the first YouTube trailer. */
    private static String trailer(JSONObject o) {
        JSONArray videos = arrayAt(o, "videos");
        String fallback = null;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v == null || !"YouTube".equals(v.optString("site")) || !"Trailer".equals(v.optString("type"))) {
                continue;
            }
            if (v.optBoolean("official")) {
                return v.optString("key");
            }
            if (fallback == null) {
                fallback = v.optString("key");
            }
        }
        return fallback;
    }

    /**
     * Where to watch in one country. The request must append watch/providers.
     * TMDB gets this data from JustWatch.
     */
    private static List<Offer> offers(JSONObject o, String country) {
        List<Offer> out = new ArrayList<>();
        JSONObject section = o.optJSONObject("watch/providers");
        JSONObject results = section == null ? null : section.optJSONObject("results");
        JSONObject here = results == null ? null : results.optJSONObject(country);
        if (here == null) {
            return out;
        }
        for (Offer.Mode mode : Offer.Mode.values()) {
            JSONArray providers = here.optJSONArray(mode.key);
            for (int i = 0; providers != null && i < providers.length(); i++) {
                JSONObject p = providers.optJSONObject(i);
                out.add(new Offer(p.optInt("provider_id"), p.optString("provider_name"), mode,
                        path(p, "logo_path")));
            }
        }
        return out;
    }

    /** The "results" array inside an appended section, or an empty array. */
    private static JSONArray arrayAt(JSONObject o, String section) {
        JSONObject s = o.optJSONObject(section);
        JSONArray a = s == null ? null : s.optJSONArray("results");
        return a == null ? new JSONArray() : a;
    }

    private static String name(JSONObject o) {
        // Movies use "title" and shows use "name".
        return o.has("title") ? o.optString("title") : o.optString("name");
    }

    private static String year(JSONObject o) {
        String date = o.has("release_date") ? o.optString("release_date") : o.optString("first_air_date");
        return date.length() >= 4 ? date.substring(0, 4) : "";
    }

    private static String path(JSONObject o, String key) {
        String p = o.optString(key, "");
        return p.isEmpty() || "null".equals(p) ? null : p;
    }
}
