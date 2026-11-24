package dev.skhanal.atrium.content;

/**
 * TMDB image addresses, at the smallest size that looks right where it is drawn.
 *
 * TMDB serves each image at a fixed set of widths. Downloading the smallest one
 * that covers the space saves time on this TV's Wi-Fi, and memory once decoded.
 */
public final class TmdbImages {

    private static final String BASE = "https://image.tmdb.org/t/p/";
    private static final int[] BACKDROP = {300, 780, 1280};
    private static final int[] POSTER = {92, 154, 185, 342, 500, 780};
    private static final int[] LOGO = {45, 92, 154, 185, 300, 500};
    /**
     * An image may be drawn up to 10% larger than it is. From a sofa that
     * stretch is invisible, and it often saves a size step.
     */
    private static final float STRETCH = 1.1f;

    private TmdbImages() {
    }

    /** @param widthPx how wide the image is drawn, in screen pixels */
    public static String backdrop(String path, int widthPx) {
        return url(path, BACKDROP, widthPx);
    }

    public static String poster(String path, int widthPx) {
        return url(path, POSTER, widthPx);
    }

    public static String logo(String path, int widthPx) {
        return url(path, LOGO, widthPx);
    }

    private static String url(String path, int[] widths, int widthPx) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int pick = widths[widths.length - 1];
        for (int w : widths) {
            if (w * STRETCH >= widthPx) {
                pick = w;
                break;
            }
        }
        return BASE + "w" + pick + path;
    }

    /**
     * How much to shrink an image while decoding it: the largest power of two
     * that still leaves it big enough on both sides. Android decodes faster and
     * into less memory at a power of two.
     */
    public static int sampleSize(int srcW, int srcH, int reqW, int reqH) {
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) {
            return 1;
        }
        int sample = 1;
        while ((srcW / (sample * 2f)) * STRETCH >= reqW && (srcH / (sample * 2f)) * STRETCH >= reqH) {
            sample *= 2;
        }
        return sample;
    }
}
