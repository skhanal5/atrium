package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A button in the hero.
 *
 * Resting it is an outline on the room. Focused it fills with paper white and
 * the text turns dark, which is the strongest contrast move the palette has and
 * leaves no doubt about what Select will do. That inversion is also readable to
 * someone who cannot separate colours, because it is a change in brightness,
 * not in hue.
 */
public final class Pill extends View {

    public static final float HEIGHT_DP = 42f;
    private static final float PAD_DP = 24f;
    private static final float TEXT_SP = 15f;
    private static final float MARK_DP = 13f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path mark = new Path();

    private String label = "";
    private boolean showPlay;

    public Pill(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setWillNotDraw(false);
        setBackground(null);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Dp.f(1.4f));
        text.setTypeface(Type.medium());
        text.setTextSize(Dp.sp(TEXT_SP));
        text.setLetterSpacing(0.02f);
    }

    public void set(String label, boolean showPlay) {
        this.label = label == null ? "" : label;
        this.showPlay = showPlay;
        setContentDescription(this.label);
        requestLayout();
        invalidate();
    }

    public String label() {
        return label;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float w = text.measureText(label) + Dp.f(PAD_DP) * 2f;
        if (showPlay) {
            w += Dp.f(MARK_DP) + Dp.f(10f);
        }
        setMeasuredDimension(Math.round(w), Dp.of(HEIGHT_DP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bounds.set(0, 0, w, h);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean on = isFocused();
        float r = getHeight() / 2f;

        fill.setColor(on ? Palette.TEXT_PRIMARY : Palette.SURFACE);
        canvas.drawRoundRect(bounds, r, r, fill);
        if (!on) {
            float half = stroke.getStrokeWidth() / 2f;
            stroke.setColor(Palette.HAIRLINE);
            bounds.inset(half, half);
            canvas.drawRoundRect(bounds, r - half, r - half, stroke);
            bounds.inset(-half, -half);
        }

        int ink = on ? Palette.INK : Palette.TEXT_PRIMARY;
        text.setColor(ink);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;

        float x = Dp.f(PAD_DP);
        if (showPlay) {
            float s = Dp.f(MARK_DP);
            float cy = getHeight() / 2f;
            mark.reset();
            mark.moveTo(x, cy - s * 0.52f);
            mark.lineTo(x + s * 0.86f, cy);
            mark.lineTo(x, cy + s * 0.52f);
            mark.close();
            fill.setColor(ink);
            canvas.drawPath(mark, fill);
            x += s + Dp.f(10f);
        }
        canvas.drawText(label, x, baseline, text);
    }

    public void applyFocus(boolean focused) {
        long d = Motion.d(Motion.FOCUS);
        float scale = focused ? 1.04f : 1f;
        animate().cancel();
        if (d == 0) {
            setScaleX(scale);
            setScaleY(scale);
        } else {
            animate().scaleX(scale).scaleY(scale)
                    .setDuration(d).setInterpolator(Motion.SWIFT).start();
        }
        invalidate();
    }
}
