package dev.skhanal.atrium.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * The hero: the one piece of type that owns the screen.
 *
 * It always describes the thing the remote is pointing at. Move along the row
 * and the name changes, the line underneath changes, and the room behind it
 * changes colour. That is the whole idea of the launcher in one sentence: the
 * screen is about what you are looking at, not about what somebody paid to put
 * there.
 *
 * Three sizes of type, one family, and a short rule in the colour of the app.
 * The hierarchy is carried by size, weight and letter spacing. A second
 * typeface would say less and cost more.
 *
 * The title auto-fits. App names are not ours and some of them are long, so the
 * size steps down until the name fits on one line rather than wrapping into the
 * row below or being cut off mid-word.
 */
public final class Hero extends View {

    private static final float EYEBROW_SP = 12f;
    private static final float TITLE_MAX_SP = 58f;
    private static final float TITLE_MIN_SP = 32f;
    private static final float BODY_SP = 14.5f;
    private static final float RULE_W_DP = 26f;
    private static final float RULE_H_DP = 2f;
    private static final float GAP_RULE_DP = 14f;
    private static final float GAP_TITLE_DP = 16f;
    private static final float GAP_BODY_DP = 20f;
    private static final float LIFT_DP = 10f;

    /** The one animation that swaps the text. Held so it can be cancelled. */
    private ValueAnimator swap;

    private final TextPaint eyebrowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rule = new RectF();

    private String eyebrow = "";
    private String title = "";
    private String body = "";
    private String titleCut = "";
    private StaticLayout bodyLayout;

    private int accent = Palette.TEXT_PRIMARY;
    private float eyebrowBaseline;
    private float titleBaseline;
    private float bodyTop;

    public Hero(Context context) {
        super(context);
        setFocusable(false);
        setWillNotDraw(false);

        eyebrowPaint.setTypeface(Type.medium());
        eyebrowPaint.setTextSize(Dp.sp(EYEBROW_SP));
        eyebrowPaint.setLetterSpacing(0.28f);
        eyebrowPaint.setColor(Palette.TEXT_TERTIARY);

        titlePaint.setTypeface(Type.light());
        titlePaint.setColor(Palette.TEXT_PRIMARY);
        titlePaint.setLetterSpacing(-0.005f);

        bodyPaint.setTypeface(Type.light());
        bodyPaint.setTextSize(Dp.sp(BODY_SP));
        bodyPaint.setColor(Palette.TEXT_SECONDARY);
    }

    /**
     * Show something new.
     *
     * The old block leaves before the new one arrives, rather than crossfading
     * through each other, because two sets of 64sp type on top of one another
     * is a smear. It is short: by the time the eye has followed the focus move,
     * the new name is already settling.
     */
    public void show(final String eyebrow, final String title, final String body,
                     final int accent) {
        long out = Motion.d(Motion.HERO_OUT);
        long in = Motion.d(Motion.HERO_IN);
        if (swap != null) {
            swap.cancel();
            swap = null;
        }
        if (out + in == 0) {
            apply(eyebrow, title, body, accent);
            setAlpha(1f);
            setTranslationY(0f);
            return;
        }

        // One animator for both halves, rather than a fade out with the fade in
        // hung off its end. A cancelled animation never runs its end action, so
        // that shape could leave the hero faded out and empty for good, which
        // is what happened when two apps were focused in quick succession.
        final float from = getAlpha();
        final long total = out + in;
        final float turn = out / (float) total;
        final float lift = Dp.f(LIFT_DP);
        final boolean[] applied = {false};

        swap = ValueAnimator.ofFloat(0f, 1f);
        swap.setDuration(total);
        swap.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (t < turn) {
                float k = Motion.SMOOTH.getInterpolation(t / turn);
                setAlpha(from * (1f - k));
                setTranslationY(-lift * 0.6f * k);
            } else {
                if (!applied[0]) {
                    applied[0] = true;
                    apply(eyebrow, title, body, accent);
                    setTranslationY(lift);
                }
                float k = Motion.SWIFT.getInterpolation((t - turn) / (1f - turn));
                setAlpha(k);
                setTranslationY(lift * (1f - k));
            }
        });
        swap.start();
    }

    private void apply(String eyebrow, String title, String body, int accent) {
        this.eyebrow = eyebrow == null ? "" : eyebrow.toUpperCase(java.util.Locale.getDefault());
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.accent = accent;
        this.titleCut = "";
        this.bodyLayout = null;
        // App names can hold characters Jost does not have. One label falling
        // back to the system font is better than a line of empty boxes.
        titlePaint.setTypeface(Type.forLabel(this.title, Type.light()));
        requestLayout();
        invalidate();
    }

    public String title() {
        return title;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        fitTitle(width);

        Paint.FontMetrics em = eyebrowPaint.getFontMetrics();
        Paint.FontMetrics tm = titlePaint.getFontMetrics();

        eyebrowBaseline = -em.ascent;
        // Measured from the cap height, not the line box, so the space above
        // the name is the space that was chosen and not whatever the font
        // happens to declare.
        titleBaseline = eyebrowBaseline + Dp.f(GAP_TITLE_DP) + (-tm.ascent * 0.78f);
        bodyTop = titleBaseline + Dp.f(GAP_BODY_DP);

        float height = bodyTop;
        if (!body.isEmpty()) {
            buildBody(width);
            height += bodyLayout.getHeight();
        }
        setMeasuredDimension(width, Math.round(height));
    }

    private void buildBody(int width) {
        bodyLayout = new StaticLayout(body, bodyPaint, width,
                Layout.Alignment.ALIGN_NORMAL, 1.22f, 0f, false);
    }

    /** Step the title down until the name fits on one line. */
    private void fitTitle(int width) {
        float size = Dp.sp(TITLE_MAX_SP);
        float min = Dp.sp(TITLE_MIN_SP);
        titlePaint.setTextSize(size);
        if (title.isEmpty() || width <= 0) {
            return;
        }
        while (size > min && titlePaint.measureText(title) > width) {
            size -= Dp.sp(2f);
            titlePaint.setTextSize(size);
        }
        titleCut = titlePaint.measureText(title) > width
                ? TextUtils.ellipsize(title, titlePaint, width, TextUtils.TruncateAt.END).toString()
                : title;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!eyebrow.isEmpty()) {
            // A short rule in the colour of the app, which is the only place
            // the accent is used at full strength in the type block.
            rulePaint.setColor(Palette.asRim(accent));
            float cy = eyebrowBaseline - eyebrowPaint.getTextSize() * 0.32f;
            rule.set(0, cy - Dp.f(RULE_H_DP) / 2f, Dp.f(RULE_W_DP), cy + Dp.f(RULE_H_DP) / 2f);
            canvas.drawRoundRect(rule, Dp.f(1f), Dp.f(1f), rulePaint);
            canvas.drawText(eyebrow, Dp.f(RULE_W_DP + GAP_RULE_DP), eyebrowBaseline, eyebrowPaint);
        }
        if (!titleCut.isEmpty()) {
            canvas.drawText(titleCut, 0, titleBaseline, titlePaint);
        }
        if (bodyLayout != null) {
            canvas.save();
            canvas.translate(0, bodyTop);
            bodyLayout.draw(canvas);
            canvas.restore();
        }
    }
}
