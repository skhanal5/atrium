package dev.skhanal.atrium.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

/**
 * The screen behind everything.
 *
 * It holds one room at a time, crossfades to the next one when focus moves to
 * another app, and lays grain over the result. That is the whole job.
 *
 * Cost per frame while idle is one stretched bitmap draw and one tiled rect.
 * During a crossfade it is two stretched draws. A Mali-T820 does not notice
 * either. Nothing in here allocates while drawing.
 */
public final class FieldView extends View {

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint grainPaint = new Paint();
    private final Rect currentSrc = new Rect();
    private final Rect outgoingSrc = new Rect();
    private final Rect full = new Rect();

    private Bitmap current;
    private Bitmap outgoing;
    private Bitmap grain;

    private float fade = 1f;        // 1 when the current room is fully in
    /** How far the room is pushed back when a section covers the screen. */
    private static final float DIM_ALPHA = 0.5f;

    private float wake = 0f;        // 0 on a cold start, 1 once the room is lit

    private ValueAnimator fadeAnim;
    private ValueAnimator wakeAnim;

    /** The flat colour shown before the first room has been generated. */
    private int fallback = 0xFF05060A;

    public FieldView(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(false);
    }

    public void setFallback(int color) {
        fallback = color;
        if (current == null) {
            invalidate();
        }
    }

    /**
     * Show a room.
     *
     * The one on screen is kept and faded out underneath the new one, so a
     * change of app is a dissolve rather than a cut. Changing rooms while a
     * previous change is still running simply starts from where it got to,
     * which is what makes fast scrubbing along the row look continuous instead
     * of flickering.
     */
    public void show(Bitmap next, boolean animate) {
        if (next == null || next == current) {
            return;
        }
        if (fadeAnim != null) {
            fadeAnim.cancel();
        }
        long d = animate ? Motion.d(Motion.FIELD) : 0;
        if (current == null || d == 0) {
            current = next;
            currentSrc.set(0, 0, next.getWidth(), next.getHeight());
            outgoing = null;
            fade = 1f;
            invalidate();
            return;
        }

        outgoing = current;
        outgoingSrc.set(currentSrc);
        current = next;
        currentSrc.set(0, 0, next.getWidth(), next.getHeight());
        fade = 0f;

        fadeAnim = ValueAnimator.ofFloat(0f, 1f);
        fadeAnim.setDuration(d);
        fadeAnim.setInterpolator(Motion.GENTLE);
        fadeAnim.addUpdateListener(a -> {
            fade = (float) a.getAnimatedValue();
            invalidate();
        });
        fadeAnim.start();
    }

    public Bitmap currentField() {
        return current;
    }

    /**
     * The one piece of theatre in the launcher: on a cold start the room comes
     * up out of the dark.
     *
     * It never blocks anything. The apps are laid out, focused and ready to
     * open on the first frame, and only the brightness is animated, so Select
     * works exactly the same during it as after it.
     */
    public void wake() {
        if (wakeAnim != null) {
            wakeAnim.cancel();
        }
        long d = Motion.d(Motion.WAKE);
        if (d == 0) {
            wake = 1f;
            invalidate();
            return;
        }
        wakeAnim = ValueAnimator.ofFloat(0f, 1f);
        wakeAnim.setDuration(d);
        wakeAnim.setInterpolator(Motion.GENTLE);
        wakeAnim.addUpdateListener(a -> {
            wake = (float) a.getAnimatedValue();
            invalidate();
        });
        wakeAnim.start();
    }

    /**
     * Push the room back while a section covers the screen, or bring it
     * forward again.
     *
     * The apps grid fills the whole width, so the artwork under it has to give
     * way or the names compete with the light. The window behind this view is
     * already the darkest colour in the field, so fading the view is the whole
     * effect. It costs one alpha and no second layer to draw.
     */
    public void setDimmed(boolean dimmed) {
        float target = dimmed ? DIM_ALPHA : 1f;
        long d = Motion.d(Motion.SECTION);
        animate().cancel();
        if (d == 0) {
            setAlpha(target);
            return;
        }
        animate().alpha(target).setDuration(d).setInterpolator(Motion.SWIFT).start();
    }

    /** Coming back from an app must not replay the opening. */
    public void setWoken() {
        if (wakeAnim != null) {
            wakeAnim.cancel();
        }
        wake = 1f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        full.set(0, 0, w, h);
        if (grain == null) {
            grain = Field.grain(128);
            grainPaint.setShader(new BitmapShader(grain,
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0) {
            return;
        }
        if (current == null) {
            paint.setColorFilter(null);
            paint.setColor(fallback);
            canvas.drawRect(full, paint);
            return;
        }

        int lit = Math.round(255f * wake);
        if (lit <= 0) {
            canvas.drawColor(0xFF000000);
            return;
        }

        boolean dissolving = outgoing != null && fade < 1f;

        // The outgoing room stays at full strength underneath. Fading both at
        // once would dip the whole screen dark in the middle of the change.
        if (dissolving) {
            paint.setAlpha(lit);
            // It is about to be covered, so it is not worth filtering while it
            // is on its way out. This is a whole screen of sampling saved on a
            // frame that already has two more passes to do.
            paint.setFilterBitmap(false);
            canvas.drawBitmap(outgoing, outgoingSrc, full, paint);
            paint.setFilterBitmap(true);
        }
        paint.setAlpha(Math.round(lit * (outgoing == null ? 1f : fade)));
        canvas.drawBitmap(current, currentSrc, full, paint);

        if (fade >= 1f) {
            outgoing = null;
        }
        // The grain is here to break up banding in a still picture. While the
        // room is dissolving there is no still picture to band, and a third
        // pass over the whole screen is the one thing this GPU cannot afford.
        // A dissolve is the only time this view draws more than once, so it is
        // the only frame worth saving work on.
        if (grain != null && !dissolving) {
            canvas.drawRect(full, grainPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (fadeAnim != null) {
            fadeAnim.cancel();
        }
        if (wakeAnim != null) {
            wakeAnim.cancel();
        }
    }
}
