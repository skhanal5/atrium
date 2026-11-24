package dev.skhanal.atrium.content;

import java.io.IOException;

/** One request to the TMDB API. Kept apart so the fetch logic can be tested without a network. */
public interface Tmdb {

    /**
     * @param pathAndQuery for example "/tv/popular?language=en-US"
     * @return the response body
     * @throws IOException when the network fails or TMDB answers with an error
     */
    String get(String pathAndQuery) throws IOException;
}
