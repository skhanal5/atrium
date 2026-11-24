package dev.skhanal.atrium.ui;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Density conversion.
 *
 * This TV composes at 1920x1080 with a density of 320dpi, so the layout is
 * 960x540 in density independent pixels and one dp is two physical pixels.
 * Every size in the launcher is written in dp against that 960x540 canvas.
 */
public final class Dp {

    private static float density = 2f;
    private static float scaledDensity = 2f;

    private Dp() {
    }

    public static void init(Context context) {
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        density = m.density;
        scaledDensity = m.scaledDensity;
    }

    public static int of(float dp) {
        return Math.round(dp * density);
    }

    public static float f(float dp) {
        return dp * density;
    }

    /** Text size in physical pixels, honouring the system font scale. */
    public static float sp(float sp) {
        return sp * scaledDensity;
    }
}
