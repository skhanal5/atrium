package dev.skhanal.atrium.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Requests to the TMDB API over HTTPS, with the read access token as a header. */
public final class TmdbHttp implements Tmdb {

    public static final String BASE = "https://api.themoviedb.org/3";
    /** Long enough for a slow Wi-Fi link, short enough that a dead one gives up. */
    private static final int TIMEOUT_MS = 10_000;

    private final String base;
    private final String token;

    /**
     * @param base  {@link #BASE}, or a local server in tests
     * @param token the TMDB read access token. Empty when the build had none.
     */
    public TmdbHttp(String base, String token) {
        this.base = base;
        this.token = token;
    }

    @Override
    public String get(String pathAndQuery) throws IOException {
        if (token == null || token.isEmpty()) {
            throw new IOException("no TMDB token in this build");
        }
        // TMDB always answers in UTF-8.
        return new String(Http.get(base + pathAndQuery, token, TIMEOUT_MS), StandardCharsets.UTF_8);
    }
}
