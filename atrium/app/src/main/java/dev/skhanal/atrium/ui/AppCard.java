package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import dev.skhanal.atrium.data.AppEntry;

/**
 * One app in the Your Apps row.
 *
 * The tile is the app's own icon, clipped to the same rounded square as every
 * other one, with the name under it. The brands are left alone. What makes the
 * row feel like one thing is the house treatment around them: one silhouette,
 * one size, one rhythm, one focus language.
 *
 * Focus is shown four ways at once and none of them is a colour on its own, so
 * the focused tile is as clear to someone who cannot separate colours as to
 * anyone else, and clear from across a room, where brightness is what the eye
 * reads first:
 *
 * - it is the only tile at full strength, the rest hold back
 * - it is larger, and grows from its foot so the row does not shuffle
 * - a rim is drawn around it, in that app's own colour
 * - the hero above takes its name, its light and its colour
 *
 * Drawing a tile is one bitmap draw, one rounded rect and one line of text. The
 * rounding and the plate behind a bare logo were done once while the apps
 * loaded.
 */
public final class AppCard extends View {

    public static final float TILE_DP = 78f;
    public static final float TOP_SPACE_DP = 12f;
    public static final float LABEL_GAP_DP = 12f;
    public static final float LABEL_SP = 12f;
    /** Focus space, tile, gap, then the name under it. */
    public static final float HEIGHT_DP = TOP_SPACE_DP + TILE_DP + LABEL_GAP_DP + 16f;
    public static final float WIDTH_DP = 104f;
    public static final float GAP_DP = 14f;

    private static final float CORNER_DP = 15f;
    private static final float FOCUSED_SCALE = 1.09f;
    private static final float RESTING_SCALE = 1f;
    private static final float RESTING_ALPHA = 0.72f;
    private static final float PRESSED_SCALE = 1.02f;

    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tile = new RectF();

    private AppEntry entry;
    private String shortLabel = "";
    private boolean arrangeMode;

    public AppCard(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setWillNotDraw(false);
        setBackground(null);

        platePaint.setColor(0x1AFFFFFF);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Dp.f(2f));
        rimPaint.setColor(Palette.TEXT_PRIMARY);

        labelPaint.setTextSize(Dp.sp(LABEL_SP));
        labelPaint.setColor(Palette.TEXT_SECONDARY);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Type.regular());

        setAlpha(RESTING_ALPHA);
    }

    public void bind(AppEntry e) {
        this.entry = e;
        setContentDescription(e == null ? null : e.label);
        labelPaint.setTypeface(e == null || e.typeface == null ? Type.regular() : e.typeface);
        shortLabel = "";
        invalidate();
    }

    public AppEntry entry() {
        return entry;
    }

    public void setArrangeMode(boolean on) {
        if (arrangeMode != on) {
            arrangeMode = on;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Dp.of(WIDTH_DP), Dp.of(HEIGHT_DP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float size = Dp.f(TILE_DP);
        float left = (w - size) / 2f;
        tile.set(left, Dp.f(TOP_SPACE_DP), left + size, Dp.f(TOP_SPACE_DP) + size);
        // Grow from the foot of the tile, so a focused app stands up rather
        // than swelling in place.
        setPivotX(w / 2f);
        setPivotY(Dp.f(TOP_SPACE_DP + TILE_DP));
        shortLabel = "";
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (entry == null) {
            return;
        }
        float r = Dp.f(CORNER_DP);

        if (entry.icon != null && !entry.icon.isRecycled()) {
            canvas.drawBitmap(entry.icon, null, tile, iconPaint);
        } else {
            canvas.drawRoundRect(tile, r, r, platePaint);
            drawInitial(canvas);
        }

        if (isFocused() || arrangeMode) {
            // The rim is the app's own colour, which is the same colour the
            // whole room has just become. It ties the two together.
            int rim = arrangeMode ? Palette.TEXT_WARN
                    : (entry.accent == 0 ? Palette.TEXT_PRIMARY
                    : Palette.asRim(entry.accent));
            rimPaint.setColor(rim);
            float half = rimPaint.getStrokeWidth() / 2f;
            // Keep the full stroke inside the tile when a scroller clips it.
            tile.inset(half, half);
            canvas.drawRoundRect(tile, r - half, r - half, rimPaint);
            tile.inset(-half, -half);
        }

        drawLabel(canvas);
    }

    private void drawLabel(Canvas canvas) {
        if (entry.label == null || entry.label.isEmpty()) {
            return;
        }
        if (shortLabel.isEmpty()) {
            shortLabel = TextUtils.ellipsize(entry.label, labelPaint,
                    getWidth() - Dp.f(6f), TextUtils.TruncateAt.END).toString();
        }
        labelPaint.setColor(isFocused() ? Palette.TEXT_PRIMARY : Palette.TEXT_SECONDARY);
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float baseline = Dp.f(TOP_SPACE_DP + TILE_DP + LABEL_GAP_DP) - fm.ascent;
        canvas.drawText(shortLabel, getWidth() / 2f, baseline, labelPaint);
    }

    private void drawInitial(Canvas canvas) {
        String label = entry.label;
        if (label == null || label.isEmpty()) {
            return;
        }
        iconPaint.setTypeface(Type.light());
        iconPaint.setTextSize(Dp.f(TILE_DP) * 0.46f);
        iconPaint.setColor(Palette.TEXT_PRIMARY);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = iconPaint.getFontMetrics();
        float baseline = tile.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label.substring(0, 1).toUpperCase(), tile.centerX(), baseline, iconPaint);
        iconPaint.setTextAlign(Paint.Align.LEFT);
    }

    /** Animate into or out of the focused look. */
    public void applyFocus(boolean focused) {
        float scale = focused ? FOCUSED_SCALE : RESTING_SCALE;
        float alpha = focused ? 1f : RESTING_ALPHA;
        long d = Motion.d(Motion.FOCUS);
        animate().cancel();
        if (d == 0) {
            setScaleX(scale);
            setScaleY(scale);
            setAlpha(alpha);
            invalidate();
            return;
        }
        animate().scaleX(scale).scaleY(scale).alpha(alpha)
                .setDuration(d).setInterpolator(Motion.SWIFT).start();
        invalidate();
    }

    /**
     * A short push, so Select feels like it landed.
     *
     * It never gates what the user asked for. The app starts on the same frame
     * as the press and this runs alongside it. An animation that has to finish
     * first is only a delay with a curve on it.
     */
    public void playPress() {
        long in = Motion.d(Motion.PRESS_IN);
        if (in == 0) {
            return;
        }
        animate().cancel();
        animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                .setDuration(in).setInterpolator(Motion.SMOOTH)
                .withEndAction(() -> animate()
                        .scaleX(FOCUSED_SCALE).scaleY(FOCUSED_SCALE)
                        .setDuration(Motion.d(Motion.PRESS_OUT))
                        .setInterpolator(Motion.SWIFT).start())
                .start();
    }
}
