package dev.skhanal.atrium.content;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A genre the owner can pick for the Your genres row.
 *
 * TMDB numbers movie genres and show genres apart, and some differ: Action is
 * 28 for movies but 10759, "Action & Adventure", for shows. So each genre is
 * matched by name to the ids on each side. A side with no match has none.
 * Ids checked against TMDB's /genre/movie/list and /genre/tv/list.
 */
public enum Genre {
    ACTION("Action", new int[] {28}, new int[] {10759}),
    ADVENTURE("Adventure", new int[] {12}, new int[] {10759}),
    ANIMATION("Animation", new int[] {16}, new int[] {16}),
    COMEDY("Comedy", new int[] {35}, new int[] {35}),
    CRIME("Crime", new int[] {80}, new int[] {80}),
    DOCUMENTARY("Documentary", new int[] {99}, new int[] {99}),
    DRAMA("Drama", new int[] {18}, new int[] {18}),
    FAMILY("Family", new int[] {10751}, new int[] {10751}),
    FANTASY("Fantasy", new int[] {14}, new int[] {10765}),
    HORROR("Horror", new int[] {27}, new int[] {}),
    KIDS("Kids", new int[] {}, new int[] {10762}),
    MYSTERY("Mystery", new int[] {9648}, new int[] {9648}),
    REALITY("Reality", new int[] {}, new int[] {10764}),
    ROMANCE("Romance", new int[] {10749}, new int[] {}),
    SCIENCE_FICTION("Science fiction", new int[] {878}, new int[] {10765}),
    THRILLER("Thriller", new int[] {53}, new int[] {}),
    WAR("War", new int[] {10752}, new int[] {10768}),
    WESTERN("Western", new int[] {37}, new int[] {37});

    /** The name the owner sees. */
    public final String label;
    private final int[] movies;
    private final int[] shows;

    Genre(String label, int[] movies, int[] shows) {
        this.label = label;
        this.movies = movies;
        this.shows = shows;
    }

    /**
     * Genres saved by their enum names. A name no longer offered is skipped,
     * so a genre removed in a later version never breaks the saved choice.
     */
    public static Set<Genre> fromNames(List<String> names) {
        Set<Genre> out = EnumSet.noneOf(Genre.class);
        for (String n : names) {
            for (Genre g : values()) {
                if (g.name().equals(n)) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** The enum names, for saving. {@link #fromNames} reads them back. */
    public static List<String> names(Set<Genre> genres) {
        List<String> out = new ArrayList<>();
        for (Genre g : genres) {
            out.add(g.name());
        }
        return out;
    }

    /** The TMDB movie genre ids for any of these genres. */
    static Set<Integer> movieIds(Set<Genre> genres) {
        return ids(genres, g -> g.movies);
    }

    /** The TMDB show genre ids for any of these genres. */
    static Set<Integer> showIds(Set<Genre> genres) {
        return ids(genres, g -> g.shows);
    }

    /** Empty when none of the genres has an id on that side. */
    private static Set<Integer> ids(Set<Genre> genres, Function<Genre, int[]> side) {
        Set<Integer> ids = new HashSet<>();
        for (Genre g : genres) {
            for (int id : side.apply(g)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
