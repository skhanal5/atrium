package dev.skhanal.atrium.content;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** One GET request. Used for TMDB data and TMDB pictures alike. */
public final class Http {

    private Http() {
    }

    /**
     * @param token a bearer token, or null to send none
     * @return the response body
     * @throws IOException when the network fails or the answer is not 2xx. The
     *                     message names the status and the path, never the
     *                     query or the body, which could echo a request.
     */
    public static byte[] get(String url, String token, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("answered " + code + " for " + c.getURL().getPath());
            }
            try (InputStream in = c.getInputStream()) {
                return Text.readBytes(in);
            }
        } finally {
            c.disconnect();
        }
    }
}
