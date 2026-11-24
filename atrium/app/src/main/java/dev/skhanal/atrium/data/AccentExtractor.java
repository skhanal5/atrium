package dev.skhanal.atrium.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.BitmapShader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Reads one colour out of an app icon.
 *
 * This is what lets the room take on the colour of the app you are pointing at.
 * The colour has to come from the icon itself, so the effect is real rather
 * than a table of brand colours we guessed.
 *
 * The icon is sampled at 32x32, which is enough to find the dominant hue and
 * cheap enough to do for every app during startup.
 */
public final class AccentExtractor {

    private static final int SAMPLE = 32;
    private static final int HUE_BUCKETS = 24;        // 15 degrees each

    private AccentExtractor() {
    }

    public static IconAnalysis analyse(Drawable icon) {
        Bitmap bmp = toBitmap(icon);
        if (bmp == null) {
            return new IconAnalysis(0, false);
        }
        try {
            return analyseBitmap(bmp);
        } finally {
            bmp.recycle();
        }
    }

    private static IconAnalysis analyseBitmap(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        float[] weight = new float[HUE_BUCKETS];
        float[] hueSum = new float[HUE_BUCKETS];
        float[] satSum = new float[HUE_BUCKETS];

        float[] hsv = new float[3];
        int edgeTotal = 0;
        int edgeClear = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                int a = (c >>> 24);

                // The outer eighth of the icon tells us whether it is a bare
                // logo or a filled square.
                boolean edge = x < w / 8 || x >= w - w / 8 || y < h / 8 || y >= h - h / 8;
                if (edge) {
                    edgeTotal++;
                    if (a < 32) {
                        edgeClear++;
                    }
                }

                if (a < 128) {
                    continue;
                }
                Color.colorToHSV(c, hsv);
                // Greys carry no hue, and near-black or near-white pixels give a
                // hue reading that is mostly noise.
                if (hsv[1] < 0.22f || hsv[2] < 0.18f) {
                    continue;
                }
                int b = (int) (hsv[0] / 360f * HUE_BUCKETS);
                if (b >= HUE_BUCKETS) {
                    b = HUE_BUCKETS - 1;
                }
                // Saturated pixels count for more, so a small bright logo beats a
                // large washed-out background.
                float wgt = hsv[1] * hsv[1];
                weight[b] += wgt;
                hueSum[b] += hsv[0] * wgt;
                satSum[b] += hsv[1] * wgt;
            }
        }

        boolean needsPlate = edgeTotal > 0 && edgeClear * 100 / edgeTotal > 55;

        int best = -1;
        float bestWeight = 0f;
        for (int i = 0; i < HUE_BUCKETS; i++) {
            if (weight[i] > bestWeight) {
                bestWeight = weight[i];
                best = i;
            }
        }

        // A grey or black and white icon has no colour worth using. Returning 0
        // tells the light to stay the colour of the time of day.
        float coverage = bestWeight / (w * h);
        if (best < 0 || coverage < 0.012f) {
            return new IconAnalysis(0, needsPlate);
        }

        hsv[0] = hueSum[best] / weight[best];
        hsv[1] = Math.min(1f, satSum[best] / weight[best]);
        hsv[2] = 1f;
        return new IconAnalysis(Color.HSVToColor(hsv), needsPlate);
    }

    private static Bitmap toBitmap(Drawable d) {
        if (d == null) {
            return null;
        }
        try {
            Bitmap out = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            d.setBounds(0, 0, SAMPLE, SAMPLE);
            d.draw(c);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // The plate behind a bare logo. It is a surface for the logo to sit on,
    // not a tile of its own, so it stays close to the room it sits in.
    private static final int PLATE = 0x1AFFFFFF;
    // Ratios against the tile size, matching AppCard's own geometry.
    private static final float CORNER_RATIO = 15f / 78f;
    private static final float INSET_RATIO = 14f / 78f;

    /**
     * Build exactly what the cell will show, once, at the size it will show it.
     *
     * Composing it here rather than in onDraw matters because the alternative
     * is a rounded clip or a save layer on every tile on every frame, and a
     * Mali-T820 charges for both. Drawing a tile is then one bitmap draw.
     */
    public static Bitmap compose(Drawable d, int sizePx, boolean needsPlate) {
        if (d == null || sizePx <= 0) {
            return null;
        }
        try {
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            RectF r = new RectF(0, 0, sizePx, sizePx);
            float corner = sizePx * CORNER_RATIO;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

            if (needsPlate) {
                p.setColor(PLATE);
                c.drawRoundRect(r, corner, corner, p);
                int inset = Math.round(sizePx * INSET_RATIO);
                d.setBounds(inset, inset, sizePx - inset, sizePx - inset);
                d.draw(c);
            } else {
                // A full icon is clipped to the same rounded square as every
                // other one, so the row has a single silhouette.
                Bitmap raw = render(d, sizePx);
                if (raw == null) {
                    return null;
                }
                p.setShader(new BitmapShader(raw, TileMode.CLAMP, TileMode.CLAMP));
                c.drawRoundRect(r, corner, corner, p);
                p.setShader(null);
            }
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** The icon drawn once at the size the row shows it, so nothing scales per frame. */
    public static Bitmap render(Drawable d, int sizePx) {
        if (d == null || sizePx <= 0) {
            return null;
        }
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                if (src != null && !src.isRecycled()
                        && src.getWidth() == sizePx && src.getHeight() == sizePx) {
                    return src;
                }
            }
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
