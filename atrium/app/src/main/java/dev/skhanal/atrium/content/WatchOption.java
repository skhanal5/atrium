package dev.skhanal.atrium.content;

import java.util.Collections;
import java.util.List;

/** One app you can watch a title in, and every way that app offers it. */
public final class WatchOption {

    public final TvApp app;
    /** Included, free, rent, buy and so on, in the order TMDB gives them. */
    public final List<Offer.Mode> modes;
    /** True when the app is on this TV. */
    public final boolean installed;
    /** A TMDB image path for the service logo, or null. */
    public final String logo;

    WatchOption(TvApp app, List<Offer.Mode> modes, boolean installed, String logo) {
        this.app = app;
        this.modes = Collections.unmodifiableList(modes);
        this.installed = installed;
        this.logo = logo;
    }

    /** True when you can watch without paying for this one title. */
    public boolean free() {
        Offer.Mode first = modes.get(0);
        return first == Offer.Mode.INCLUDED || first == Offer.Mode.FREE || first == Offer.Mode.FREE_WITH_ADS;
    }
}
