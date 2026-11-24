package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.animation.ValueAnimator;
import android.view.View;

/**
 * One choice in a menu.
 *
 * The menus in Atrium are not cards or panels. They are words in the dark,
 * over a dimmed room. That keeps them in the same language as the rest of the
 * launcher, which is type and light rather than boxes and borders, and it
 * means a menu costs almost nothing to draw.
 *
 * Focus brightens the word and slides a short rule in on its left. The rule
 * matters: brightness alone is a colour cue, and the mark gives a second,
 * positional one.
 */
public final class SheetRow extends View {

    private static final float SIZE_SP = 23f;
    private static final float PAD_V_DP = 13f;
    private static final float MARK_W_DP = 3f;
    private static final float MARK_GAP_DP = 20f;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String text = "";
    private String shown = "";
    private String value;
    private float baseline;
    private float mark = 0f;
    private boolean enabled = true;
    private ValueAnimator ruleAnim;

    public SheetRow(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setWillNotDraw(false);
        setBackground(null);
        paint.setTypeface(Type.light());
        paint.setTextSize(Dp.sp(SIZE_SP));
        paint.setColor(Palette.TEXT_SECONDARY);
        markPaint.setColor(Palette.TEXT_PRIMARY);
        valuePaint.setTypeface(Type.medium());
        valuePaint.setTextSize(Dp.sp(13f));
        valuePaint.setLetterSpacing(0.18f);
        valuePaint.setColor(Palette.TEXT_TERTIARY);
    }

    public void setText(String v) {
        text = v == null ? "" : v;
        shown = text;
        setContentDescription(text);
        requestLayout();
        invalidate();
    }

    /** The current setting, shown on the right of the row. */
    public void setValue(String v) {
        value = v;
        fit();
        invalidate();
    }

    public void setRowEnabled(boolean on) {
        enabled = on;
        setFocusable(on);
        paint.setColor(on ? Palette.TEXT_SECONDARY : Palette.TEXT_FAINT);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        float pv = Dp.f(PAD_V_DP);
        baseline = pv + -fm.ascent;
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                (int) Math.ceil(pv * 2 + -fm.ascent + fm.descent));
        fit();
    }

    /**
     * Keep the label clear of the value.
     *
     * The two are drawn from opposite edges of the same row, so a long label
     * and a long value ran straight through each other. The label gives way,
     * because the value is the part that changes and the part the user came to
     * read.
     */
    private void fit() {
        int w = getMeasuredWidth();
        if (w <= 0 || text.isEmpty()) {
            shown = text;
            return;
        }
        float left = Dp.f(MARK_W_DP + MARK_GAP_DP);
        float room = w - left;
        if (value != null) {
            room -= valuePaint.measureText(value) + Dp.f(24f);
        }
        shown = room <= 0 ? ""
                : TextUtils.ellipsize(text, paint, room, TextUtils.TruncateAt.END).toString();
    }

    public void applyFocus(boolean focused) {
        if (!enabled) {
            return;
        }
        paint.setColor(focused ? Palette.TEXT_PRIMARY : Palette.TEXT_SECONDARY);
        valuePaint.setColor(focused ? Palette.TEXT_SECONDARY : Palette.TEXT_TERTIARY);
        long d = Motion.d(Motion.FOCUS);
        if (d == 0) {
            mark = focused ? 1f : 0f;
            invalidate();
            return;
        }
        if (ruleAnim != null) {
            ruleAnim.cancel();
        }
        ValueAnimator va = ValueAnimator.ofFloat(mark, focused ? 1f : 0f);
        va.setDuration(d);
        va.setInterpolator(Motion.SWIFT);
        va.addUpdateListener(a -> {
            mark = (float) a.getAnimatedValue();
            invalidate();
        });
        va.start();
        ruleAnim = va;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = Dp.f(MARK_W_DP + MARK_GAP_DP);
        canvas.drawText(shown, x, baseline, paint);

        if (mark > 0.01f) {
            Paint.FontMetrics fm = paint.getFontMetrics();
            float capTop = baseline + fm.ascent * 0.76f;
            float capBottom = baseline;
            float cy = (capTop + capBottom) / 2f;
            float half = (capBottom - capTop) / 2f * mark;
            canvas.drawRect(0, cy - half, Dp.f(MARK_W_DP), cy + half, markPaint);
        }

        if (value != null) {
            float vw = valuePaint.measureText(value);
            canvas.drawText(value, getWidth() - vw, baseline, valuePaint);
        }
    }
}
