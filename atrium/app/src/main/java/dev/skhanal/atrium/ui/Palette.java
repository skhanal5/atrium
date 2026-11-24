package dev.skhanal.atrium.ui;

import android.graphics.Color;

/**
 * Every colour in Atrium.
 *
 * The launcher has one visual idea: a dark room with a single soft light in it.
 * So the palette is not a list of flat UI colours. It is a small set of neutrals
 * for text, plus a model of what the light looks like at a given hour.
 */
public final class Palette {

    private Palette() {
    }

    // ---- Neutrals -------------------------------------------------------
    // Text is a warm off-white, never pure white. Pure white on a dark field
    // looks like a computer display. A paper white reads as printed and is
    // easier to look at for a long time.
    public static final int TEXT_PRIMARY = 0xFFF5F3EF;
    // These were raised after looking at the screen from across a room. The
    // values that looked refined on a desk monitor were unreadable on a 43 inch
    // panel from eight feet, which is the only distance that counts here.
    public static final int TEXT_SECONDARY = 0x96F5F3EF;  // 59%
    public static final int TEXT_TERTIARY = 0x78F5F3EF;   // 47%
    public static final int TEXT_FAINT = 0x5FF5F3EF;      // 37%

    /** A warning colour for the one case that needs it: the TV is offline. */
    public static final int TEXT_WARN = 0xFFE8B079;

    /** Dark ink, for text on a paper-white surface such as the focused button. */
    public static final int INK = 0xFF0B0C10;
    /** The faint surface a resting control sits on. */
    public static final int SURFACE = 0x1AFFFFFF;
    /** The line around a resting control. */
    public static final int HAIRLINE = 0x5CFFFFFF;
    /** A scrim pulled over the room when a panel is in front of it. */
    public static final int SCRIM = 0xB8050609;

    // ---- Light presets --------------------------------------------------
    // A preset is the room at one time of day: the two ends of the background
    // gradient, the colour of the key light, and how strong that light is.

    public static final int LIGHT_AUTO = 0;
    public static final int LIGHT_WARM = 1;
    public static final int LIGHT_COOL = 2;
    public static final int LIGHT_MONO = 3;

    public static final class Light {
        public final int baseTop;
        public final int baseBottom;
        public final int key;
        public final float intensity;

        Light(int baseTop, int baseBottom, int key, float intensity) {
            this.baseTop = baseTop;
            this.baseBottom = baseBottom;
            this.key = key;
            this.intensity = intensity;
        }
    }

    // The rooms are darker than they first were, and the light is stronger. A
    // bright, evenly lit background turned the whole screen the same middle
    // grey. Letting the far side of the room fall away to near black is what
    // makes the light read as light.
    //
    // Dawn: a low, warm light just coming into the room.
    private static final Light DAWN = new Light(0xFF120D09, 0xFF050407, 0xFFFFB877, 0.46f);
    // Day: higher and cooler, the brightest the room gets.
    private static final Light DAY = new Light(0xFF0D1118, 0xFF040509, 0xFFD8E8FF, 0.50f);
    // Dusk: warm again, and lower, with a little rose in it.
    private static final Light DUSK = new Light(0xFF140E0B, 0xFF060407, 0xFFFF9A6B, 0.48f);
    // Night: deep and blue, dim enough to watch in a dark room.
    private static final Light NIGHT = new Light(0xFF080B14, 0xFF030408, 0xFF6E96D4, 0.30f);

    private static final Light FIXED_WARM = new Light(0xFF140E0B, 0xFF060407, 0xFFFFA978, 0.48f);
    private static final Light FIXED_COOL = new Light(0xFF0D1118, 0xFF040509, 0xFFCFE3FF, 0.48f);
    private static final Light FIXED_MONO = new Light(0xFF0F1013, 0xFF040406, 0xFFE8E6E2, 0.40f);

    /**
     * The light for a given preset. For {@link #LIGHT_AUTO} the hour of the day
     * picks the preset, so the room looks different in the morning and at night.
     *
     * @param hourOfDay 0-23
     */
    public static Light lightFor(int preset, int hourOfDay) {
        switch (preset) {
            case LIGHT_WARM:
                return FIXED_WARM;
            case LIGHT_COOL:
                return FIXED_COOL;
            case LIGHT_MONO:
                return FIXED_MONO;
            default:
                break;
        }
        if (hourOfDay >= 5 && hourOfDay < 8) {
            return DAWN;
        }
        if (hourOfDay >= 8 && hourOfDay < 17) {
            return DAY;
        }
        if (hourOfDay >= 17 && hourOfDay < 21) {
            return DUSK;
        }
        return NIGHT;
    }

    // ---- Mixing ---------------------------------------------------------

    /** Straight linear blend of two opaque colours. {@code t} of 0 returns {@code a}. */
    public static int mix(int a, int b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /**
     * Tone an app's accent colour down until it can be used as light in the room.
     *
     * A raw brand colour used at full strength looks like a neon sign. The light
     * has to stay believable, so the colour is capped in saturation, lifted to a
     * consistent brightness, and then blended most of the way back towards the
     * room's own light.
     */
    public static int asRoomLight(int accent, int baseKey) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(hsv[1], 0.45f);   // never fully saturated
        hsv[2] = 0.92f;                      // light is always bright, colour comes from hue
        int toned = Color.HSVToColor(hsv);
        // Most of the room light stays the time-of-day colour. The app only
        // leans it in its direction. On the real panel 0.55 was too far: a
        // bright green icon turned the whole left of the screen green and the
        // icons next to it went muddy.
        return mix(baseKey, toned, 0.38f);
    }

    /**
     * The app's colour, bright enough to draw a 2dp line with.
     *
     * The room light is a wash and has to stay subtle. A rim is a thin line on
     * a dark field, so it needs the opposite treatment: keep the hue, hold the
     * saturation back only a little, and run it at full brightness, or it
     * disappears from eight feet away.
     */
    public static int asRim(int accent) {
        if (accent == 0) {
            return TEXT_PRIMARY;
        }
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(hsv[1], 0.62f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    /** Replace the alpha of a colour, keeping its red, green and blue. */
    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
