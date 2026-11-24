package dev.skhanal.atrium.ui;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * The artwork.
 *
 * A streaming service fills its home screen with key art it owns. A launcher on
 * this TV has none. The app banners that Fire OS exposes are flat logo plates,
 * 320x180 at best, and blown up to the size of a hero they look like what they
 * are. So the art here is not fetched. It is generated.
 *
 * Every app gets its own room: a deep field of colour taken from that app's own
 * icon, an aperture of light, shafts leaning across it, a floor the light falls
 * on, and grain over everything. The structure is seeded with the package name,
 * so two apps never get the same room and one app always gets the same room.
 *
 * None of it is drawn per frame. A room is computed once into a small bitmap on
 * a background thread and then stretched over the screen, which is one bitmap
 * draw. Everything in here is smooth, so the bilinear filtering that comes free
 * with the upscale is doing useful work: it is what makes the light soft with
 * no blur pass anywhere.
 */
public final class Field {

    /** Generated at this size, then stretched to 1920x1080. */
    public static final int GEN_W = 512;
    public static final int GEN_H = 288;

    /** Where the floor begins, as a fraction of height. */
    private static final float HORIZON = 0.80f;
    private static final float HORIZON_SOFT = 0.06f;

    /** The light leans this far off vertical, the way a low sun does. */
    private static final float LEAN = 0.34f;

    /** The aperture: the one bright thing in the room, and the focal point. */
    private static final float AP_X = 0.78f;
    private static final float AP_Y = 0.24f;
    private static final float AP_RX = 0.40f;
    private static final float AP_RY = 0.44f;

    /**
     * The left of the screen is held down to this fraction of the light, so the
     * hero type always has a dark field under it whatever colour the app is.
     * This is the one rule that keeps the layout readable across every app.
     */
    private static final float LEFT_FLOOR = 0.05f;
    private static final float LEFT_EDGE = 0.56f;

    private Field() {
    }

    /** What one room is made of. */
    public static final class Spec {
        final int glow;
        final int deep;
        final int shadow;
        final float intensity;
        final int seed;

        Spec(int glow, int deep, int shadow, float intensity, int seed) {
            this.glow = glow;
            this.deep = deep;
            this.shadow = shadow;
            this.intensity = intensity;
            this.seed = seed;
        }
    }

    /**
     * Work out the room for one app.
     *
     * The accent is the colour read out of the app's icon. It is never used as
     * it is. A brand colour at full strength looks like a neon sign, so the
     * light is capped in saturation and the deep end of the room is a far
     * darker, slightly richer version of the same hue. Only the hue really
     * comes from the app. Everything else is the house style.
     *
     * @param accent the colour from the icon, or 0 for an icon with no colour
     * @param light  the time of day, which still sets the mood underneath
     * @param key    anything stable per app, normally the package name
     */
    public static Spec specFor(int accent, Palette.Light light, String key) {
        float[] hsv = new float[3];
        if (accent == 0) {
            // A grey icon lends no colour, so the room keeps the hour's own light.
            Color.colorToHSV(light.key, hsv);
        } else {
            Color.colorToHSV(accent, hsv);
        }

        float h = hsv[0];
        float s = hsv[1];

        hsv[0] = h;
        hsv[1] = Math.min(s, 0.44f);
        hsv[2] = 1f;
        int glow = Color.HSVToColor(hsv);

        // The lit side of the room keeps more of the colour and almost none of
        // the brightness. The difference between a rich shadow and a pale light
        // is most of what makes a field of colour look lit rather than painted.
        hsv[1] = Math.min(s * 1.15f, 0.76f);
        hsv[2] = 0.19f;
        int deep = Color.HSVToColor(hsv);

        // The side away from the light is not the same colour turned down. It
        // is the opposite colour, nearly black. Warm key, cool shadow is how a
        // lit room actually photographs, and it is the single thing that makes
        // a flat tint look like a photograph instead of a colour wash.
        hsv[0] = (h + 168f) % 360f;
        hsv[1] = 0.55f;
        hsv[2] = 0.085f;
        int shadow = Color.HSVToColor(hsv);

        return new Spec(glow, deep, shadow, light.intensity, seedOf(key));
    }

    /** A small stable number per app, used to place the shafts. */
    private static int seedOf(String key) {
        if (key == null) {
            return 1;
        }
        int h = 17;
        for (int i = 0; i < key.length(); i++) {
            h = h * 31 + key.charAt(i);
        }
        return h == 0 ? 1 : h;
    }

    /**
     * Build the full size room. Call this off the main thread: it touches about
     * 150k pixels and is far too slow to sit inside a frame.
     */
    public static Bitmap build(Spec spec) {
        int w = GEN_W;
        int h = GEN_H;
        int[] px = new int[w * h];

        float gr = ((spec.glow >> 16) & 0xFF) / 255f;
        float gg = ((spec.glow >> 8) & 0xFF) / 255f;
        float gb = (spec.glow & 0xFF) / 255f;

        float dr = ((spec.deep >> 16) & 0xFF) / 255f;
        float dg = ((spec.deep >> 8) & 0xFF) / 255f;
        float db = (spec.deep & 0xFF) / 255f;

        float sr = ((spec.shadow >> 16) & 0xFF) / 255f;
        float sg = ((spec.shadow >> 8) & 0xFF) / 255f;
        float sb = (spec.shadow & 0xFF) / 255f;

        // Four shafts, placed from the seed. Even spacing reads as a pattern,
        // and a pattern reads as wallpaper rather than as light.
        int n = 4;
        float[] bx = new float[n];
        float[] bw = new float[n];
        float[] bi = new float[n];
        int rnd = spec.seed;
        for (int i = 0; i < n; i++) {
            rnd = rnd * 1103515245 + 12345;
            float a = ((rnd >>> 8) & 0xFFFF) / 65535f;
            rnd = rnd * 1103515245 + 12345;
            float b = ((rnd >>> 8) & 0xFFFF) / 65535f;
            rnd = rnd * 1103515245 + 12345;
            float c = ((rnd >>> 8) & 0xFFFF) / 65535f;
            bx[i] = 0.36f + i * 0.20f + a * 0.11f;
            bw[i] = 0.016f + b * 0.042f;
            bi[i] = 0.42f + c * 0.58f;
        }

        for (int y = 0; y < h; y++) {
            float fy = (float) y / (h - 1);

            // ---- the shell: ceiling, wall, floor ----
            // The wall lifts a little towards the ceiling and the floor falls
            // away from the horizon, so the room has a top and a bottom even
            // before any light lands in it.
            float wall = 1.18f - 0.55f * fy;
            float floorT = Math.max(0f, (fy - HORIZON) / (1f - HORIZON));
            float floor = 0.34f + 0.30f * floorT;
            float m = smooth((fy - (HORIZON - HORIZON_SOFT)) / (2f * HORIZON_SOFT));
            float shell = wall + (floor - wall) * m;

            // Light that lands on the floor is reflected back, dimmer and
            // squashed. One line of maths, and it is what stops the bottom of
            // the screen reading as a black band under the picture.
            float mirrorY = fy > HORIZON ? HORIZON - (fy - HORIZON) * 0.78f : -1f;
            float mirrorFade = fy > HORIZON ? (1f - floorT) * 0.42f : 0f;

            float damp = 1f - 0.78f * smooth((fy - 0.40f) / 0.46f);

            for (int x = 0; x < w; x++) {
                float fx = (float) x / (w - 1);

                // ---- the aperture ----
                float core = aperture(fx, fy);
                float litAmount = core;
                if (mirrorY > 0f) {
                    litAmount += aperture(fx, mirrorY) * mirrorFade;
                }

                // ---- the shafts ----
                float u = fx - LEAN * fy;
                float beam = 0f;
                for (int i = 0; i < n; i++) {
                    float away = Math.abs(u - bx[i]);
                    float d = away / bw[i];
                    if (d < 1f) {
                        float t = 1f - d;
                        beam += bi[i] * t * t * (3f - 2f * t);
                    }
                    // The halo. A beam with only a bright core reads as a
                    // scratch on the screen. Light in dusty air glows around
                    // the bright part, and that is what makes it look like
                    // light in a room.
                    float hd = away / (bw[i] * 3.4f);
                    if (hd < 1f) {
                        float t = 1f - hd;
                        beam += bi[i] * 0.26f * t * t * (3f - 2f * t);
                    }
                }
                if (beam > 1.4f) {
                    beam = 1.4f;
                }
                // Shafts only exist near the aperture and fade as they cross
                // the room, or they look like stripes painted on the wall.
                litAmount += beam * reach(fx, fy) * 0.66f;

                // ---- hold the left down ----
                // Everything the user reads sits on the left, so the light is
                // kept off it. The field is still lit there, just far enough
                // down that 64sp type never has to fight it.
                float side = LEFT_FLOOR + (1f - LEFT_FLOOR) * smooth(fx / LEFT_EDGE);
                // The rows live across the bottom, so the light is taken off
                // it as well. The artwork owns the top right and the content
                // sits on near black underneath, which is the only way type
                // this small stays readable over a picture.
                litAmount *= side * damp * spec.intensity * 1.45f;
                if (litAmount > 1f) {
                    litAmount = 1f;
                }

                // An S curve, so the darks go properly dark and the lit part
                // carries the picture. A straight ramp is what made the first
                // version read as one flat tint across the whole screen.
                float lit = litAmount * litAmount * (3f - 2f * litAmount);

                // The unlit end is the cool shadow, the lit end the app's own
                // colour, and the two are blended by how much light there is.
                float baseR = sr + (dr - sr) * side;
                float baseG = sg + (dg - sg) * side;
                float baseB = sb + (db - sb) * side;
                baseR *= shell;
                baseG *= shell;
                baseB *= shell;

                float r = baseR + (gr - baseR) * lit;
                float g = baseG + (gg - baseG) * lit;
                float b = baseB + (gb - baseB) * lit;

                // Bloom. Where the light is strongest it washes towards white,
                // the way an overexposed highlight does. Without this the
                // brightest part of the picture is still just the app's colour
                // and the whole thing stays flat.
                if (lit > 0.82f) {
                    float t = (lit - 0.82f) / 0.18f * 0.30f;
                    r += (1f - r) * t;
                    g += (1f - g) * t;
                    b += (1f - b) * t;
                }

                // ---- vignette ----
                float vx = (fx - 0.56f) * 2f;
                float vy = (fy - 0.48f) * 2f;
                float vd = (float) Math.sqrt(vx * vx * 0.74f + vy * vy);
                float vig = (vd - 0.52f) / 0.88f;
                if (vig > 0f) {
                    if (vig > 1f) {
                        vig = 1f;
                    }
                    float k = 1f - vig * vig * 0.70f;
                    r *= k;
                    g *= k;
                    b *= k;
                }

                px[y * w + x] = 0xFF000000
                        | (to8(r) << 16) | (to8(g) << 8) | to8(b);
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** The soft ellipse of light, as a 0..1 brightness. */
    private static float aperture(float fx, float fy) {
        float dx = (fx - AP_X) / AP_RX;
        float dy = (fy - AP_Y) / AP_RY;
        float d2 = dx * dx + dy * dy;
        if (d2 > 3.6f) {
            return 0f;
        }
        return (float) Math.exp(-2.15f * d2);
    }

    /** How much of the shafts survives this far from the aperture. */
    private static float reach(float fx, float fy) {
        float dx = (fx - 0.80f) * 0.86f;
        float dy = (fy - 0.16f) * 0.70f;
        float d = 1f - (float) Math.sqrt(dx * dx + dy * dy) * 1.30f;
        return d <= 0f ? 0f : d * d;
    }

    /**
     * The tile behind one app in the row.
     *
     * The same colour as that app's room, composed as a small panel rather than
     * a room: a diagonal wash with a highlight in the corner the light would
     * come from. Focusing a card and seeing the whole screen become the same
     * colour is the link that holds the two halves of the screen together.
     */
    public static Bitmap tile(Spec spec, int w, int h) {
        int[] px = new int[w * h];

        float gr = ((spec.glow >> 16) & 0xFF) / 255f;
        float gg = ((spec.glow >> 8) & 0xFF) / 255f;
        float gb = (spec.glow & 0xFF) / 255f;
        float dr = ((spec.deep >> 16) & 0xFF) / 255f;
        float dg = ((spec.deep >> 8) & 0xFF) / 255f;
        float db = (spec.deep & 0xFF) / 255f;

        for (int y = 0; y < h; y++) {
            float fy = (float) y / (h - 1);
            for (int x = 0; x < w; x++) {
                float fx = (float) x / (w - 1);

                // A wash running from the bottom left up to the top right.
                float t = smooth(fx * 0.58f + (1f - fy) * 0.42f);
                // The highlight, in the corner the room's own light comes from.
                float hx = (fx - 0.80f) / 0.62f;
                float hy = (fy - 0.16f) / 0.72f;
                float hot = (float) Math.exp(-2.2f * (hx * hx + hy * hy));

                // Far less light than the hero gets. A chip is a dark panel
                // with the colour only suggested in it. At full strength four
                // of them in a row fight each other and the screen.
                float lit = t * 0.20f + hot * 0.26f;
                if (lit > 1f) {
                    lit = 1f;
                }
                float r = dr * 0.52f + (gr - dr * 0.52f) * lit;
                float g = dg * 0.52f + (gg - dg * 0.52f) * lit;
                float b = db * 0.52f + (gb - db * 0.52f) * lit;

                // A little darker at the very bottom, so the card sits down on
                // the shelf instead of floating.
                float foot = fy > 0.72f ? (fy - 0.72f) / 0.28f : 0f;
                float k = 1f - foot * foot * 0.34f;

                px[y * w + x] = 0xFF000000
                        | (to8(r * k) << 16) | (to8(g * k) << 8) | to8(b * k);
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /**
     * A tile of fine noise, laid over everything.
     *
     * This is the cheapest quality in the launcher. A dark gradient on an 8-bit
     * panel bands into visible steps, and a little noise breaks the steps up so
     * the field looks continuous. It also gives the picture a film grain, which
     * is most of why it stops looking like a computer background.
     *
     * It has to be weak, for a reason specific to this TV: the panel is 4K but
     * Android draws at 1080p, so every drawn pixel is shown as a 2x2 block and
     * noise is four times larger in area than it looks while developing. It is
     * also half light and half dark, because white-only noise can only lighten,
     * which lifts the blacks instead of dithering them.
     */
    public static Bitmap grain(int size) {
        int[] px = new int[size * size];
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) {
            int a = (int) Math.abs(rnd.nextGaussian() * 1.4);
            if (a > 4) {
                a = 4;
            }
            px[i] = (a << 24) | (rnd.nextBoolean() ? 0x00FFFFFF : 0x00000000);
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }

    private static float smooth(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return t * t * (3f - 2f * t);
    }

    private static int to8(float v) {
        int i = (int) (v * 255f + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
