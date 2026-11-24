package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * One destination in the left rail.
 *
 * There are three states to tell apart, not two, and they must all read from
 * the sofa:
 *
 * - resting: a quiet mark and a quiet word
 * - current: the section you are in. Its word is at full strength and a short
 *   bar sits against its left edge
 * - focused: a filled pill, so the remote's position is never in doubt
 *
 * The bar for "current" is deliberately not a colour. Colour alone would say
 * nothing to a viewer who cannot separate colours, and nothing at all from
 * across a room.
 */
public final class RailItem extends View {

    public static final float WIDTH_DP = 168f;
    public static final float HEIGHT_DP = 50f;
    private static final float GLYPH_DP = 21f;
    private static final float TEXT_SP = 15f;
    private static final float PAD_DP = 16f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private String label = "";
    private int kind = Glyph.HOME;
    private boolean current;
    private int accent = Palette.TEXT_PRIMARY;

    public RailItem(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setWillNotDraw(false);
        setBackground(null);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Dp.f(1.4f));
        text.setTypeface(Type.regular());
        text.setTextSize(Dp.sp(TEXT_SP));
    }

    public void set(String label, int glyphKind) {
        this.label = label == null ? "" : label;
        this.kind = glyphKind;
        setContentDescription(this.label);
        invalidate();
    }

    public void setCurrent(boolean on) {
        if (current != on) {
            current = on;
            invalidate();
        }
    }

    public boolean isCurrent() {
        return current;
    }

    /** The colour of the room right now, used for the focused outline. */
    public void setAccent(int color) {
        if (accent != color) {
            accent = color;
            if (isFocused()) {
                invalidate();
            }
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Dp.of(WIDTH_DP), Dp.of(HEIGHT_DP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bounds.set(0, 0, w, h);
        setPivotX(0f);
        setPivotY(h / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean on = isFocused();
        float r = Dp.f(13f);

        if (on) {
            fill.setColor(0x2EFFFFFF);
            canvas.drawRoundRect(bounds, r, r, fill);
            float half = stroke.getStrokeWidth() / 2f;
            stroke.setColor(Palette.withAlpha(accent, 0.85f));
            bounds.inset(half, half);
            canvas.drawRoundRect(bounds, r - half, r - half, stroke);
            bounds.inset(-half, -half);
        } else if (current) {
            fill.setColor(0x14FFFFFF);
            canvas.drawRoundRect(bounds, r, r, fill);
        }

        int ink = on || current ? Palette.TEXT_PRIMARY : Palette.TEXT_SECONDARY;
        glyph.setColor(ink);
        float gx = Dp.f(PAD_DP) + Dp.f(GLYPH_DP) / 2f;
        Glyph.draw(canvas, kind, gx, getHeight() / 2f, Dp.f(GLYPH_DP), glyph);

        text.setColor(ink);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, gx + Dp.f(GLYPH_DP) / 2f + Dp.f(16f), baseline, text);

    }

    public void applyFocus(boolean focused) {
        long d = Motion.d(Motion.FOCUS);
        float x = focused ? Dp.f(4f) : 0f;
        animate().cancel();
        if (d == 0) {
            setTranslationX(x);
        } else {
            animate().translationX(x).setDuration(d)
                    .setInterpolator(Motion.SWIFT).start();
        }
        invalidate();
    }
}
