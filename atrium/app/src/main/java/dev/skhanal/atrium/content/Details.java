package dev.skhanal.atrium.content;

import java.util.Collections;
import java.util.List;

/** What the detail screen shows about a title. Empty text means TMDB had none. */
public final class Details {

    /** The age rating in the chosen country, such as "PG-13" or "TV-MA". */
    public final String rating;
    /** The TMDB user score out of 10, to one decimal place. 0 when unrated. */
    public final double score;
    /** "2h 35m" for a movie or "5 seasons" for a show. */
    public final String length;
    /** At most three, in TMDB's order. */
    public final List<String> genres;
    public final String overview;
    public final String tagline;
    /** A YouTube video ID, or null when there is no trailer. */
    public final String trailer;
    /** Where to watch in the chosen country. Empty when TMDB lists nothing. */
    public final List<Offer> offers;

    public Details(String rating, double score, String length, List<String> genres,
            String overview, String tagline, String trailer, List<Offer> offers) {
        this.rating = rating;
        this.score = score;
        this.length = length;
        this.genres = Collections.unmodifiableList(genres);
        this.overview = overview;
        this.tagline = tagline;
        this.trailer = trailer;
        this.offers = Collections.unmodifiableList(offers);
    }
}
