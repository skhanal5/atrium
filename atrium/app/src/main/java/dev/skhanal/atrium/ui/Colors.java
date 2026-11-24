package dev.skhanal.atrium.ui;

/**
 * Every colour on the new screens.
 *
 * The artwork carries the colour. Everything Atrium draws itself is a warm
 * off-white on near-black ink, so it never competes with the picture behind it.
 */
public final class Colors {

    private Colors() {
    }

    /** Text. A warm white reads as print. Pure white on black reads as a computer screen. */
    public static final int TEXT = 0xFFF4F1EC;
    /** Secondary text, at 74%. Readable from a sofa on this 43 inch panel. */
    public static final int TEXT_2 = 0xBDF4F1EC;
    /** Tertiary text, at 52%: labels and credits. */
    public static final int TEXT_3 = 0x85F4F1EC;

    /** The ink everything sits on, and text on a focused white control. */
    public static final int INK = 0xFF0A0D14;
    /** A resting control or card. */
    public static final int SURFACE = 0x1FFFFFFF;
    public static final int SURFACE_2 = 0x33FFFFFF;
    /** The screen behind a menu. */
    public static final int SCRIM = 0xF00A0D14;

    /** An app you have: "Open". */
    public static final int OK = 0xFF8FE3A4;
    /** An app you could get: "Get the app". */
    public static final int GET = 0xFFFFC56E;
    /** The one warning: the TV is offline. */
    public static final int WARN = 0xFFE8B079;
}
