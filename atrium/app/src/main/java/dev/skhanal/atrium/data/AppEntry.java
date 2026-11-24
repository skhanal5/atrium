package dev.skhanal.atrium.data;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Typeface;

/** One launchable app, with everything the home screen needs to draw it. */
public final class AppEntry {

    public final String pkg;
    public final ComponentName component;
    public final String label;

    /** Drawn at the exact size the row shows, so no scaling happens per frame. */
    public Bitmap icon;
    /** When this launcher last started this app, or 0 if it never has. */
    public long lastOpened;
    /** The colour this app gives its room, or 0 for an icon with no colour. */
    public int accent;
    /** True when the icon is a bare logo and needs a surface behind it. */
    public boolean needsPlate;
    /** Jost, or the system font when the name needs characters Jost lacks. */
    public Typeface typeface;

    public AppEntry(String pkg, ComponentName component, String label) {
        this.pkg = pkg;
        this.component = component;
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppEntry)) {
            return false;
        }
        return pkg.equals(((AppEntry) o).pkg);
    }

    @Override
    public int hashCode() {
        return pkg.hashCode();
    }
}
