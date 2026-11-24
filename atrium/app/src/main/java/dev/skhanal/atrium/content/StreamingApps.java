package dev.skhanal.atrium.content;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which TMDB streaming services have an app on Fire TV, and which of those
 * apps are on this TV.
 *
 * Services are matched by TMDB number, not by name. Names change: TMDB renamed
 * "Apple TV+" to "Apple TV" in 2026. Channels sold inside another app, such as
 * "HBO Max Amazon Channel", have numbers of their own and are left out.
 *
 * Prime Video is left out on purpose. On Fire TV it closes again as soon as
 * another app opens it, which was tested on this TV, so Atrium could offer
 * nothing but a button that does nothing.
 *
 * Every package name here was checked on the TV, by opening its Amazon
 * Appstore page. Starz and MGM+ are left out because no Fire TV name for them
 * could be confirmed, and a "Get the app" that opens "App not Found" is worse
 * than not offering it.
 */
public final class StreamingApps {

    static final List<TvApp> ALL = Arrays.asList(
            TvApp.of("YouTube", new String[] {"com.amazon.firetv.youtube"}, new Integer[] {}, 192)
                    .withLogo("/5Maob4o5w8oZnNeYpCDyVFD3M7X.png").withTopics("videos"),
            TvApp.of("Peacock", new String[] {"com.peacock.peacockfiretv"}, new Integer[] {386, 387})
                    .withLogo("/a1UIdq5BrkcAxnxcUhFsNbXnxeu.png").withTopics("movies", "tv", "sports"),
            TvApp.of("Apple TV", new String[] {"com.apple.atve.amazon.appletv"}, new Integer[] {350}, 2)
                    .withLogo("/v9SnyvYt0U3KGgBH3Sbh3z4yODe.png").withTopics("movies", "tv"),
            TvApp.of("Netflix", new String[] {"com.netflix.ninja"}, new Integer[] {8, 1796})
                    .withLogo("/rK1KljqmbvO9HQa1PBFLILWah72.png").withTopics("movies", "tv"),
            TvApp.of("Disney+", new String[] {"com.disney.disneyplus"}, new Integer[] {337})
                    .withLogo("/5eZ872CghnHFLB1j8grszbrx0dx.png").withTopics("movies", "kids", "family"),
            TvApp.of("HBO Max", new String[] {"com.hbo.hbonow"}, new Integer[] {1899})
                    .withLogo("/skypuy7SXuugIQeYg0IglmzoKaS.png").withTopics("movies", "tv"),
            TvApp.of("Hulu", new String[] {"com.hulu.plus"}, new Integer[] {15})
                    .withLogo("/44uAnmSqvA4yBOdbPWN8YgQHjWm.png").withTopics("tv", "movies", "live"),
            TvApp.of("Paramount+", new String[] {"com.cbs.ott"}, new Integer[] {2303, 2616})
                    .withLogo("/4N4BMd0Mm0kHAmF7RZgL5lW3cwc.png").withTopics("movies", "tv", "sports"),
            TvApp.of("Tubi", new String[] {"com.tubitv.ott"}, new Integer[] {73})
                    .withLogo("/9dEuvA8wg5TSeFBZlPxSVxFdimJ.png").withTopics("free", "movies"),
            TvApp.of("Pluto TV", new String[] {"tv.pluto.android"}, new Integer[] {300})
                    .withLogo("/fN4czqaMQNLeF6sSSIjGbAWzvwK.png").withTopics("free", "live"),
            TvApp.of("Crunchyroll", new String[] {"com.crunchyroll.crunchyroid"}, new Integer[] {283})
                    .withLogo("/uFL3c4Cq8M6WoLymlC5Y8bmGytV.png").withTopics("anime"),
            TvApp.of("Plex", new String[] {"com.plexapp.android"}, new Integer[] {538})
                    .withLogo("/blAEhvx3XX8sV0fFVFk88FFjubs.png").withTopics("free", "movies", "media", "player"),
            TvApp.of("Philo", new String[] {"com.philo.philo"}, new Integer[] {2383})
                    .withLogo("/mfbhzQwmQKjm7bLZPF5CTeu3Ilc.png").withTopics("live", "tv"));

    private StreamingApps() {
    }

    /** The app that plays this TMDB service, or null when Fire TV has none. */
    public static TvApp forProvider(int providerId) {
        for (TvApp app : ALL) {
            if (app.plays(providerId)) {
                return app;
            }
        }
        return null;
    }

    /**
     * Every app on Fire TV that has this title. Installed apps where it is
     * free to watch come first, then other installed apps, then apps you could
     * get. Within each group TMDB's order is kept.
     */
    public static List<WatchOption> whereToWatch(List<Offer> offers, Set<String> installed) {
        Map<TvApp, List<Offer>> byApp = new LinkedHashMap<>();
        for (Offer o : offers) {
            TvApp app = forProvider(o.providerId);
            if (app != null) {
                byApp.computeIfAbsent(app, a -> new ArrayList<>()).add(o);
            }
        }
        List<WatchOption> out = new ArrayList<>();
        for (Map.Entry<TvApp, List<Offer>> e : byApp.entrySet()) {
            List<Offer.Mode> modes = new ArrayList<>();
            for (Offer o : e.getValue()) {
                if (!modes.contains(o.mode)) {
                    modes.add(o.mode);
                }
            }
            out.add(new WatchOption(e.getKey(), modes, e.getKey().installedIn(installed),
                    e.getValue().get(0).logo));
        }
        // A stable sort, so TMDB's order holds inside each group.
        out.sort(Comparator.comparingInt(w -> (w.installed ? 0 : 2) + (w.free() ? 0 : 1)));
        return out;
    }

    /** The installed app where this title is included or free, or null. */
    public static WatchOption watchHere(List<Offer> offers, Set<String> installed) {
        for (WatchOption w : whereToWatch(offers, installed)) {
            if (w.installed && w.free()) {
                return w;
            }
        }
        return null;
    }

    /** Installed apps with services you subscribe to or watch free, in table order. */
    public static List<TvApp> subscriptionApps(Set<String> installed) {
        List<TvApp> out = new ArrayList<>();
        for (TvApp app : ALL) {
            if (!app.included.isEmpty() && app.installedIn(installed)) {
                out.add(app);
            }
        }
        return out;
    }

    /** TMDB numbers of the services you can watch in installed apps. */
    public static Set<Integer> includedServices(Set<String> installed) {
        Set<Integer> out = new HashSet<>();
        for (TvApp app : subscriptionApps(installed)) {
            out.addAll(app.included);
        }
        return out;
    }
}
