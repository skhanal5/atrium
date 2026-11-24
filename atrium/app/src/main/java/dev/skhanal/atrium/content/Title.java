package dev.skhanal.atrium.content;

/** One movie or show from TMDB, with what the home screen needs to draw it. */
public final class Title {

    public enum Kind {
        MOVIE("movie"), TV("tv");

        /** The word TMDB uses in its paths and its media_type field. */
        public final String path;

        Kind(String path) {
            this.path = path;
        }

        static Kind from(String path) {
            for (Kind k : values()) {
                if (k.path.equals(path)) {
                    return k;
                }
            }
            return null;
        }
    }

    /** The kind and the TMDB number together, for example "movie-438631". */
    public final String id;
    public final Kind kind;
    public final int tmdbId;
    public final String name;
    /** The release year, or empty when TMDB has no date. */
    public final String year;
    /** TMDB image paths, such as "/abc.jpg". Null when there is none. */
    public final String backdrop;
    public final String poster;
    /** Null until the details for this title are loaded. */
    public final Details details;

    public Title(Kind kind, int tmdbId, String name, String year, String backdrop, String poster) {
        this(kind, tmdbId, name, year, backdrop, poster, null);
    }

    public Title(Kind kind, int tmdbId, String name, String year, String backdrop, String poster,
            Details details) {
        this.details = details;
        this.id = kind.path + "-" + tmdbId;
        this.kind = kind;
        this.tmdbId = tmdbId;
        this.name = name;
        this.year = year;
        this.backdrop = backdrop;
        this.poster = poster;
    }
}
