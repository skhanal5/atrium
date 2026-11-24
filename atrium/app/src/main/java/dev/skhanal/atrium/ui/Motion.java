package dev.skhanal.atrium.ui;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * The timing side of the design system.
 *
 * Two rules hold the motion together:
 *
 * 1. Focus is fast, the room is slow. The thing the remote controls reacts at
 *    once. The light and the background drift behind it. That difference is
 *    what makes the room feel large and the input feel immediate.
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
    /** Slow and even, for the light and the background. */
    public static final Interpolator GENTLE = new PathInterpolator(0.33f, 0f, 0.1f, 1f);

    public static final long FOCUS = 220;
    public static final long LABEL = 180;
    public static final long LIGHT = 420;
    public static final long PRESS_IN = 90;
    public static final long PRESS_OUT = 160;
    public static final long WAKE = 700;
    public static final long SHEET = 200;
    /** The room dissolving into the next app's room. */
    public static final long FIELD = 420;
    /** Hero type leaving, then the next one arriving behind it. */
    public static final long HERO_OUT = 130;
    public static final long HERO_IN = 260;
    /** The rail opening out to show its labels. */
    public static final long RAIL = 190;
    /** A whole section being swapped for another. */
    public static final long SECTION = 240;

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
