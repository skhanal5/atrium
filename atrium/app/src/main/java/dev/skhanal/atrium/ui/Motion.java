package dev.skhanal.atrium.ui;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * The timing side of the design.
 *
 * Two rules hold the motion together:
 *
 * 1. Focus is fast, the picture is slow. What the remote controls reacts at
 *    once. The artwork dissolves behind it. That difference makes the screen
 *    feel calm and the input feel immediate.
 * 2. Nothing ever makes the user wait. Every animation is decorative. Input is
 *    accepted during all of them.
 */
public final class Motion {

    private Motion() {
    }

    /** Decisive ease-out. Leaves fast, settles softly. Used for anything focused. */
    public static final Interpolator SWIFT = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** The everyday curve, for fades and small moves. */
    public static final Interpolator SMOOTH = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    /** Slow and even, for the artwork. */
    public static final Interpolator GENTLE = new PathInterpolator(0.33f, 0f, 0.1f, 1f);

    public static final long FOCUS = 220;
    public static final long SHEET = 200;
    /** Hero text leaving, then the next title arriving behind it. */
    public static final long HERO_OUT = 130;
    public static final long HERO_IN = 260;
    /** A row sliding sideways, or the rows scrolling up and down. */
    public static final long SECTION = 240;
    /** One picture dissolving into the next behind the screen. */
    public static final long BACKDROP = 600;
    /**
     * How long the hero shows one trending title before the next. This is a
     * pace, not an animation, so reduce motion does not change it.
     */
    public static final long HERO_ROTATE = 8000;

    /**
     * Set to 0 when the user turns on "reduce motion". Durations are multiplied
     * by this, so every animation still ends in the correct state, it just gets
     * there at once.
     */
    private static float scale = 1f;

    public static void setReduced(boolean reduced) {
        scale = reduced ? 0f : 1f;
    }

    public static boolean isReduced() {
        return scale == 0f;
    }

    /** A duration with the reduce-motion setting applied. */
    public static long d(long base) {
        return (long) (base * scale);
    }
}
