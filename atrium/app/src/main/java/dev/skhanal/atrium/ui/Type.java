package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * The typefaces, loaded once.
 *
 * Atrium uses one family, Jost, in three weights. One family keeps every screen
 * in the same voice. Size, weight and letter spacing carry the hierarchy instead
 * of a second typeface.
 *
 * The files are static weights. Jost ships as a variable font, and Android 7.1
 * cannot read variable axes, so asking for 'wght' at runtime would silently give
 * the same weight everywhere.
 */
public final class Type {

    private static Typeface light;
    private static Typeface regular;
    private static Typeface medium;

    /** Used when a label contains characters Jost does not have. */
    private static final Typeface FALLBACK = Typeface.SANS_SERIF;

    private Type() {
    }

    public static void init(Context context) {
        if (light != null) {
            return;
        }
        light = load(context, "fonts/Jost-Light.ttf", Typeface.NORMAL);
        regular = load(context, "fonts/Jost-Regular.ttf", Typeface.NORMAL);
        medium = load(context, "fonts/Jost-Medium.ttf", Typeface.BOLD);
    }

    private static Typeface load(Context context, String path, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(context.getAssets(), path);
        } catch (RuntimeException e) {
            // A missing or broken font must never stop the launcher from opening.
            return Typeface.create(Typeface.SANS_SERIF, fallbackStyle);
        }
    }

    public static Typeface light() {
        return light != null ? light : FALLBACK;
    }

    public static Typeface regular() {
        return regular != null ? regular : FALLBACK;
    }

    public static Typeface medium() {
        return medium != null ? medium : FALLBACK;
    }

    /**
     * The typeface to use for a label that came from another app.
     *
     * App names are not ours, so they can hold any character. Jost covers Latin
     * only. If a name needs something it does not have, that one label falls
     * back to the system font, which has the full set. Without this the name
     * would draw as empty boxes.
     */
    public static Typeface forLabel(String label, Typeface preferred) {
        if (label == null || label.isEmpty()) {
            return preferred;
        }
        Paint p = new Paint();
        p.setTypeface(preferred);
        int i = 0;
        while (i < label.length()) {
            int cp = label.codePointAt(i);
            int n = Character.charCount(cp);
            String ch = label.substring(i, i + n);
            // Whitespace is always safe and hasGlyph is false for some of it.
            if (!Character.isWhitespace(cp) && !p.hasGlyph(ch)) {
                return FALLBACK;
            }
            i += n;
        }
        return preferred;
    }
}
