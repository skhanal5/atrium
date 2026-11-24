package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The large title at the top of the home screen: what it is, its facts, two
 * lines about it, where you can watch it on this TV, and two buttons.
 *
 * When a new title arrives the text fades out and back in, rather than
 * crossfading through itself. Two sets of large type on top of each other
 * read as a smear.
 */
public final class HeroView extends LinearLayout {

    static final float WIDTH_DP = 450f;

    private final TextView eyebrow;
    private final TextView title;
    private final TextView facts;
    private final TextView overview;
    private final TextView watchHere;
    private final LinearLayout text;
    public final Chip info;
    public final Chip trailer;
    private final Dots dots;
    private final LinearLayout buttons;

    public HeroView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);

        text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        eyebrow = label(context, Type.semibold(), 10f, Colors.TEXT_2);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setAllCaps(true);
        title = label(context, Type.bold(), 52f, Colors.TEXT);
        title.setLetterSpacing(-0.02f);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0f, 0.95f);
        title.setShadowLayer(Dp.f(10f), 0, Dp.f(2f), 0x66000000);
        facts = label(context, Type.medium(), 11.5f, Colors.TEXT_2);
        overview = label(context, Type.regular(), 12f, Colors.TEXT_2);
        overview.setMaxLines(2);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(0f, 1.4f);
        watchHere = label(context, Type.semibold(), 10.5f, Colors.OK);

        text.addView(eyebrow);
        text.addView(title, margins(7f, 8f));
        text.addView(facts);
        text.addView(overview, margins(8f, 0f));
        text.addView(watchHere, margins(8f, 0f));

        buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setClipChildren(false);
        info = new Chip(context, Chip.Style.BUTTON);
        trailer = new Chip(context, Chip.Style.BUTTON);
        buttons.addView(info);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(Dp.of(9f));
        buttons.addView(trailer, tp);
        addView(buttons, margins(14f, 0f));

        dots = new Dots(context);
        addView(dots, margins(12f, 0f));
    }

    private static TextView label(Context c, android.graphics.Typeface face, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(sp));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static LayoutParams margins(float topDp, float bottomDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Dp.of(topDp);
        lp.bottomMargin = Dp.of(bottomDp);
        return lp;
    }

    /**
     * Show a title.
     *
     * @param factsList  the facts line, joined with dots
     * @param watchLine  where to watch on this TV, or null
     * @param hasTrailer false fades the Trailer button
     */
    public void show(String eyebrowText, String name, List<String> factsList, String about,
            String watchLine, boolean hasTrailer) {
        Runnable apply = () -> {
            eyebrow.setText(eyebrowText);
            title.setTypeface(Type.forLabel(name, Type.bold()));
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(sizeFor(name)));
            title.setText(name);
            facts.setText(TextUtils.join("  ·  ", factsList));
            facts.setVisibility(factsList.isEmpty() ? GONE : VISIBLE);
            overview.setText(about);
            overview.setVisibility(about == null || about.isEmpty() ? GONE : VISIBLE);
            watchHere.setText(watchLine);
            watchHere.setVisibility(watchLine == null ? GONE : VISIBLE);
        };
        trailer.setChipEnabled(hasTrailer);
        long out = Motion.d(Motion.HERO_OUT);
        if (out == 0 || title.getText().length() == 0) {
            apply.run();
            return;
        }
        text.animate().cancel();
        text.animate().alpha(0f).setDuration(out).setInterpolator(Motion.SMOOTH).withEndAction(() -> {
            apply.run();
            text.animate().alpha(1f).setDuration(Motion.d(Motion.HERO_IN)).setInterpolator(Motion.SWIFT).start();
        }).start();
    }

    /** Buttons only make sense for a movie or show, not for an app. */
    public void setButtonsVisible(boolean on) {
        buttons.setVisibility(on ? VISIBLE : INVISIBLE);
    }

    /** Long names step down in size so they stay on one or two lines. */
    private static float sizeFor(String name) {
        int n = name.length();
        return n <= 16 ? 52f : n <= 26 ? 44f : n <= 40 ? 35f : 29f;
    }

    /** Which trending title is showing, or -1 to hide the marks. */
    public void setPage(int index, int count) {
        dots.set(index, count);
    }

    /** Short marks under the buttons, one per trending title in turn. */
    private static final class Dots extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int index = -1;
        private int count;

        Dots(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        void set(int i, int n) {
            index = i;
            count = n;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Dp.of(120f), Dp.of(3f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (index < 0) {
                return;
            }
            float w = Dp.f(17f);
            float gap = Dp.f(5f);
            for (int i = 0; i < count; i++) {
                paint.setColor(i == index ? Colors.TEXT : Colors.SURFACE_2);
                float x = i * (w + gap);
                r.set(x, 0, x + w, getHeight());
                canvas.drawRoundRect(r, getHeight() / 2f, getHeight() / 2f, paint);
            }
        }
    }
}
