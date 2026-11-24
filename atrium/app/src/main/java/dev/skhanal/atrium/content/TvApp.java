package dev.skhanal.atrium.content;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A streaming app that runs on Fire TV, and the TMDB services it plays. */
public final class TvApp {

    public final String name;
    /**
     * Fire TV package names for this app. The first is the one the Appstore
     * knows. Any others are older names it may still be installed under.
     */
    public final List<String> packages;
    /** TMDB services you subscribe to, or watch free, in this app. */
    final Set<Integer> included;
    /** TMDB services that rent and sell titles in this app. */
    final Set<Integer> store;

    TvApp(String name, List<String> packages, Set<Integer> included, Set<Integer> store) {
        this.name = name;
        this.packages = Collections.unmodifiableList(packages);
        this.included = included;
        this.store = store;
    }

    static TvApp of(String name, String[] packages, Integer[] included, Integer... store) {
        return new TvApp(name, Arrays.asList(packages),
                new HashSet<>(Arrays.asList(included)), new HashSet<>(Arrays.asList(store)));
    }

    /** The package name the Amazon Appstore knows this app by, checked on the TV. */
    public String appstorePackage() {
        return packages.get(0);
    }

    /** True when any of this app's packages is installed. */
    public boolean installedIn(Set<String> installed) {
        return installedPackage(installed) != null;
    }

    /** The package name this TV has the app under, or null when it is not installed. */
    public String installedPackage(Set<String> installed) {
        for (String p : packages) {
            if (installed.contains(p)) {
                return p;
            }
        }
        return null;
    }

    boolean plays(int providerId) {
        return included.contains(providerId) || store.contains(providerId);
    }
}
