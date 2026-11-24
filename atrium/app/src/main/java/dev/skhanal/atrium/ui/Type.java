package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * The typefaces, loaded once.
 *
 * Atrium uses one family, Inter, in four weights. Inter was drawn for screens,
 * so it stays clear at sofa distance, and it is plain enough to sit under movie
 * artwork without competing with it.
 *
 * The files are static weights cut down to Latin characters. Inter ships as a
 * variable font, and Android 7.1 cannot read variable axes, so a variable file
 * would draw every weight the same.
 */
public final class Type {

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface semibold;
    private static Typeface bold;

    /** Used when a label contains characters the Latin cut does not have. */
    private static final Typeface FALLBACK = Typeface.SANS_SERIF;

    private Type() {
    }

    public static void init(Context context) {
        if (regular != null) {
            return;
        }
        regular = load(context, "fonts/Inter-Regular.ttf", Typeface.NORMAL);
        medium = load(context, "fonts/Inter-Medium.ttf", Typeface.NORMAL);
        semibold = load(context, "fonts/Inter-SemiBold.ttf", Typeface.BOLD);
        bold = load(context, "fonts/Inter-Bold.ttf", Typeface.BOLD);
    }

    private static Typeface load(Context context, String path, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(context.getAssets(), path);
        } catch (RuntimeException e) {
            // A missing or broken font must never stop the launcher from opening.
            return Typeface.create(Typeface.SANS_SERIF, fallbackStyle);
        }
    }

    public static Typeface regular() {
        return regular != null ? regular : FALLBACK;
    }

    public static Typeface medium() {
        return medium != null ? medium : FALLBACK;
    }

    public static Typeface semibold() {
        return semibold != null ? semibold : FALLBACK;
    }

    public static Typeface bold() {
        return bold != null ? bold : FALLBACK;
    }

    /**
     * The typeface to use for text that came from someone else, such as an app
     * name or a film title.
     *
     * The Latin cut has no Greek, Cyrillic or CJK. If a name needs a character
     * it does not have, that one label falls back to the system font, which
     * has the full set. Without this the name would draw as empty boxes.
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
