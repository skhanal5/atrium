package dev.skhanal.atrium.content;

/** One way to watch a title: a streaming service and how you pay for it. */
public final class Offer {

    /** In the order TMDB data is read, which is also the order shown. */
    public enum Mode {
        INCLUDED("flatrate"), FREE("free"), FREE_WITH_ADS("ads"), RENT("rent"), BUY("buy");

        /** The key TMDB uses for this mode. */
        final String key;

        Mode(String key) {
            this.key = key;
        }
    }

    /** TMDB's number for the service. Used to find titles on the services you have. */
    public final int providerId;
    /** TMDB's name for the service, such as "Peacock Premium". */
    public final String provider;
    public final Mode mode;
    /** A TMDB image path for the service logo, or null. */
    public final String logo;

    public Offer(int providerId, String provider, Mode mode, String logo) {
        this.providerId = providerId;
        this.provider = provider;
        this.mode = mode;
        this.logo = logo;
    }
}
