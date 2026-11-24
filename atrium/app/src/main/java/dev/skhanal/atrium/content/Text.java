package dev.skhanal.atrium.content;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * UTF-8 text helpers that work on Android 7.1.
 *
 * The Charset overloads of URLEncoder.encode and ByteArrayOutputStream.toString
 * need Android 13. The build tool rewrites some newer Java methods for
 * old Android, such as List.of and String.join, but not these two, and lint
 * does not flag them. Calling them would crash on this TV, so the encoding is
 * named instead.
 */
public final class Text {

    private static final String UTF_8 = "UTF-8";

    private Text() {
    }

    /** Encode a value for a URL query. */
    public static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is always supported", e);
        }
    }

    /** Read a whole stream as UTF-8. The caller closes it. */
    public static String read(InputStream in) throws IOException {
        return new String(readBytes(in), UTF_8);
    }

    /** Read a whole stream. The caller closes it. */
    public static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        for (int n = in.read(buf); n != -1; n = in.read(buf)) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
