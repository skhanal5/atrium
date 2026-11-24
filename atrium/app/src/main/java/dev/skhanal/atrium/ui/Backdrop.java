package dev.skhanal.atrium.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import dev.skhanal.atrium.content.TmdbImages;

/**
 * The artwork behind everything, with a crossfade from one picture to the next.
 *
 * This TV's graphics chip, a Mali-T820, cannot afford three full-screen layers
 * at 60 frames a second. So the dark shading that keeps text readable is not
 * drawn over the picture each frame. It is baked into the picture once, on a
 * background thread, by {@link #SHADE}. Drawing the screen is then one bitmap,
 * or two while one picture dissolves into the next.
 */
public final class Backdrop extends View {

    /** Shaded pictures are made at half the screen size and stretched. They hold no fine detail. */
    static final int SHADE_W = 960;
    static final int SHADE_H = 540;

    /**
     * Darkens the left side and the bottom of a picture, where the text and the
     * rows sit, and leaves the top right bright.
     */
    public static final Images.Transform SHADE = new Images.Transform() {
        @Override
        public String key() {
            return "shade2";
        }

        /**
         * Made in RGB_565, half the memory of full colour. Dark gradients band
         * into visible steps at that depth, so the shading is dithered.
         */
        @Override
        public Bitmap apply(Bitmap src) {
            Bitmap out = Bitmap.createBitmap(SHADE_W, SHADE_H, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(out);
            c.drawColor(Colors.INK);
            c.drawBitmap(src, centerCrop(src.getWidth(), src.getHeight(), SHADE_W, SHADE_H),
                    new RectF(0, 0, SHADE_W, SHADE_H), new Paint(Paint.FILTER_BITMAP_FLAG));
            Paint p = new Paint();
            p.setDither(true);
            p.setShader(new LinearGradient(0, 0, SHADE_W, 0,
                    new int[] {ink(0.97f), ink(0.86f), ink(0.35f), ink(0f)},
                    new float[] {0f, 0.26f, 0.52f, 0.70f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SHADE_W, SHADE_H, p);
            p.setShader(new LinearGradient(0, SHADE_H, 0, 0,
                    new int[] {ink(1f), ink(0.92f), ink(0.2f), ink(0f)},
                    new float[] {0f, 0.34f, 0.62f, 0.78f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SHADE_W, SHADE_H, p);
            return out;
        }
    };

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final RectF dst = new RectF();
    private Bitmap current;
    private Bitmap previous;
    /** How far the current picture has faded in, 0 to 1. */
    private float mix = 1f;
    private ValueAnimator fade;
    /** The picture last asked for. A late arrival for an older request is dropped. */
    private String wanted;

    public Backdrop(Context context) {
        super(context);
        setWillNotDraw(false);
        dimPaint.setColor(Colors.INK);
        dimPaint.setAlpha(0);
    }

    /**
     * Show a TMDB backdrop, shaded. If it is not loaded yet, the current
     * picture stays until it is, so moving fast never flashes black.
     */
    public void showArtwork(String backdropPath) {
        String url = TmdbImages.backdrop(backdropPath, SHADE_W * 2);
        wanted = url;
        Bitmap b = load(url, (k, bmp) -> {
            if (url.equals(wanted)) {
                show(bmp);
            }
        });
        if (b != null) {
            show(b);
        }
    }

    /** Load a backdrop now so it is ready when it is shown. */
    public void preload(String backdropPath) {
        load(TmdbImages.backdrop(backdropPath, SHADE_W * 2), (k, bmp) -> { });
    }

    private Bitmap load(String url, Images.Callback callback) {
        return Images.get(getContext()).load(url, SHADE_W, SHADE_H, true, SHADE, callback);
    }

    private void show(Bitmap shaded) {
        if (shaded == current) {
            return;
        }
        if (fade != null) {
            fade.cancel();
        }
        previous = current;
        current = shaded;
        long d = Motion.d(Motion.BACKDROP);
        if (d == 0 || previous == null) {
            mix = 1f;
            previous = null;
            invalidate();
            return;
        }
        mix = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(d);
        fade.setInterpolator(Motion.GENTLE);
        fade.addUpdateListener(a -> {
            mix = (float) a.getAnimatedValue();
            if (mix >= 1f) {
                previous = null;
            }
            invalidate();
        });
        fade.start();
    }

    /**
     * Darken the whole picture, for the detail screen, where text runs further
     * across it. One extra rect, drawn only while the screen is still.
     */
    public void setDim(float amount) {
        dimPaint.setAlpha(Math.round(255 * amount));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Colors.INK);
        dst.set(0, 0, getWidth(), getHeight());
        if (previous != null && mix < 1f) {
            paint.setAlpha(255);
            canvas.drawBitmap(previous, null, dst, paint);
        }
        if (current != null) {
            paint.setAlpha(Math.round(255 * mix));
            canvas.drawBitmap(current, null, dst, paint);
        }
        if (dimPaint.getAlpha() > 0) {
            canvas.drawRect(dst, dimPaint);
        }
    }

    private static int ink(float alpha) {
        return (Math.round(alpha * 255) << 24) | (Colors.INK & 0x00FFFFFF);
    }

    /** The part of a picture that fills a box of another shape without stretching. */
    private static Rect centerCrop(int w, int h, int boxW, int boxH) {
        float scale = Math.max(boxW / (float) w, boxH / (float) h);
        int cw = Math.round(boxW / scale);
        int ch = Math.round(boxH / scale);
        int x = (w - cw) / 2;
        int y = (h - ch) / 2;
        return new Rect(x, y, x + cw, y + ch);
    }
}
