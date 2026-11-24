package dev.skhanal.atrium.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words the home and detail screens use for titles and where to watch them.
 *
 * Kept in plain Java rather than string resources so the wording can be
 * tested. Atrium is English only, like the TMDB data it shows.
 */
public final class WatchText {

    private WatchText() {
    }

    /** "Included", "Rent or buy", "Included, rent or buy". */
    public static String modes(List<Offer.Mode> modes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.size(); i++) {
            if (i > 0) {
                sb.append(i == modes.size() - 1 ? " or " : ", ");
            }
            String word = word(modes.get(i));
            sb.append(i == 0 ? word : word.toLowerCase(Locale.US));
        }
        return sb.toString();
    }

    private static String word(Offer.Mode m) {
        switch (m) {
            case INCLUDED:
                return "Included";
            case FREE:
                return "Free";
            case FREE_WITH_ADS:
                return "Free with ads";
            case RENT:
                return "Rent";
            default:
                return "Buy";
        }
    }

    /** What pressing the option does. */
    public static String action(WatchOption w) {
        return w.installed ? "Open" : "Get the app";
    }

    /** The line under the hero, such as "Included with Peacock". */
    public static String watchHere(WatchOption w) {
        Offer.Mode m = w.modes.get(0);
        return word(m) + (m == Offer.Mode.INCLUDED ? " with " : " on ") + w.app.name;
    }

    /** The facts line: score, year, age rating, length, two genres. Missing facts are left out. */
    public static List<String> facts(Title t) {
        return facts(t, true);
    }

    /** The facts line, without genres when the screen shows them on their own. */
    public static List<String> facts(Title t, boolean withGenres) {
        List<String> out = new ArrayList<>();
        Details d = t.details;
        if (d != null && d.score > 0) {
            out.add(String.format(Locale.US, "★ %.1f", d.score));
        }
        if (!t.year.isEmpty()) {
            out.add(t.year);
        }
        if (d == null) {
            return out;
        }
        if (!d.rating.isEmpty()) {
            out.add(d.rating);
        }
        if (!d.length.isEmpty()) {
            out.add(d.length);
        }
        if (withGenres && !d.genres.isEmpty()) {
            out.add(String.join(", ", d.genres.subList(0, Math.min(2, d.genres.size()))));
        }
        return out;
    }
}
