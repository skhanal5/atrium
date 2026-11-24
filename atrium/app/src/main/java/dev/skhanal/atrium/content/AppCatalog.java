package dev.skhanal.atrium.content;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every app the Get apps screen offers: the streaming apps, plus a few other
 * popular Fire TV apps.
 *
 * Each package name opened its own Amazon Appstore page on this TV. Twitch,
 * YouTube TV, Kodi and Pandora were tried and opened "App not Found", so they
 * are not here.
 */
public final class AppCatalog {

    /** Apps with no TMDB services. Their logos are not on TMDB, so they draw an initial. */
    private static final List<TvApp> OTHERS = Arrays.asList(
            TvApp.app("Spotify", "com.spotify.tv.android").withTopics("music", "podcasts"),
            TvApp.app("Amazon Music", "com.amazon.bueller.music").withTopics("podcasts"),
            TvApp.app("Sling TV", "com.sling").withTopics("live", "sports", "tv"),
            TvApp.app("ESPN", "com.espn.gtv").withTopics("sports", "live"),
            TvApp.app("Fubo", "com.fubo.firetv.screen").withTopics("sports", "live", "tv"),
            TvApp.app("VLC", "org.videolan.vlc").withTopics("player", "media", "videos"));

    public static final List<TvApp> ALL;

    static {
        List<TvApp> all = new ArrayList<>(StreamingApps.ALL);
        all.addAll(OTHERS);
        ALL = Collections.unmodifiableList(all);
    }

    private AppCatalog() {
    }

    /**
     * Apps matching what was typed. A blank search lists every app.
     *
     * Apps whose name has a word starting with the search come first, then
     * apps with a topic starting with it. Each group keeps the catalog order.
     * Case and symbols are ignored, so "disney" finds "Disney+".
     */
    public static List<TvApp> search(String typed) {
        String q = words(typed == null ? "" : typed);
        if (q.isEmpty()) {
            return ALL;
        }
        List<TvApp> byName = new ArrayList<>();
        List<TvApp> byTopic = new ArrayList<>();
        for (TvApp app : ALL) {
            if (startsAWord(words(app.name), q)) {
                byName.add(app);
            } else if (anyStartsWith(app.topics, q)) {
                byTopic.add(app);
            }
        }
        byName.addAll(byTopic);
        return byName;
    }

    /** Lower case, with anything that is not a letter or digit as one space. */
    private static String words(String s) {
        return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean startsAWord(String name, String q) {
        return name.startsWith(q) || name.contains(" " + q);
    }

    private static boolean anyStartsWith(List<String> topics, String q) {
        for (String t : topics) {
            if (t.startsWith(q)) {
                return true;
            }
        }
        return false;
    }
}
