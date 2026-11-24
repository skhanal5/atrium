package dev.skhanal.atrium.content;

/**
 * Links that open other apps on this TV. Each format was checked on the TV
 * with "cmd package resolve-activity" before it was used here.
 */
public final class Links {

    /** The YouTube app on Fire TV. Name it in the intent, or Android asks which app to use. */
    public static final String YOUTUBE_PACKAGE = "com.amazon.firetv.youtube";

    private Links() {
    }

    /** A YouTube link for a trailer, or null when there is no video. */
    public static String trailer(String youtubeKey) {
        if (youtubeKey == null || youtubeKey.isEmpty()) {
            return null;
        }
        return "https://www.youtube.com/watch?v=" + youtubeKey;
    }

    /**
     * A link that opens one app's Amazon Appstore page, or null without a package.
     *
     * Tested on this TV: a page link opens the app's page with its Download
     * button, and a wrong package name opens "App not Found". A search link
     * ("?s=") does not search at all on Fire OS 6. It opens the Amazon home
     * screen. So every app is reached by a package name checked on the TV.
     */
    public static String appstorePage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        return "amzn://apps/android?p=" + Text.urlEncode(pkg);
    }
}
