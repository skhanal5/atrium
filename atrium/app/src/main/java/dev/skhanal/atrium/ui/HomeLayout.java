package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

/**
 * Where everything on the home screen goes.
 *
 * The TV composes at 1920x1080 at 320dpi, so this is a 960x540dp canvas and one
 * dp is two physical pixels. Every number below is in dp against that canvas,
 * and all of them live here rather than being spread through the views, so the
 * composition can be read in one place.
 *
 * The screen is built in four bands down the left two thirds, with the light
 * and the artwork filling the right third behind them:
 *
 *   28   the clock, quiet, above the rail
 *  116   the rail: where you are and where else you can go
 *   92   the hero: what you are pointing at, named in large type
 *        its two actions, 16 under whatever the hero measured
 *  300   "your apps", then the row of apps
 *  444   "tools", then the row of destinations
 *
 * A TV may crop up to 5% of the picture, which is 27dp here. Nothing that
 * matters sits outside a 28dp margin.
 */
public final class HomeLayout extends ViewGroup {

    public static final float RAIL_X = 28f;
    public static final float RAIL_TOP = 116f;
    public static final float RAIL_GAP = 6f;

    public static final float CONTENT_X = 236f;
    public static final float RIGHT_MARGIN = 60f;

    private static final float CLOCK_X = 30f;
    private static final float CLOCK_TOP = 28f;

    private static final float HERO_TOP = 92f;
    private static final float HERO_W = 470f;
    /** The two actions sit under the hero, which is as tall as its own text needs. */
    private static final float GAP_PILLS = 16f;

    private static final float APPS_LABEL_BASE = 300f;
    private static final float APPS_TOP = 312f;
    private static final float TOOLS_LABEL_BASE = 444f;
    private static final float TOOLS_TOP = 454f;

    /** The app row starts above the icons to include each card's focus space. */
    public static final float APPS_HEADROOM = AppCard.TOP_SPACE_DP;
    public static final float TOOLS_HEADROOM = 6f;

    private final Paint noticePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private View clock;
    private View rail;
    private View hero;
    private View pills;
    private View appsLabel;
    private View appsRow;
    private View toolsLabel;
    private View toolsRow;
    private View empty;

    private String notice;
    private float noticeWidth;

    public HomeLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);

        noticePaint.setTypeface(Type.medium());
        noticePaint.setTextSize(Dp.sp(11f));
        noticePaint.setLetterSpacing(0.20f);
        noticePaint.setColor(Palette.TEXT_WARN);
    }

    public void attach(View clock, View rail, View hero, View pills, View appsLabel,
                       View appsRow, View toolsLabel, View toolsRow, View empty) {
        this.clock = clock;
        this.rail = rail;
        this.hero = hero;
        this.pills = pills;
        this.appsLabel = appsLabel;
        this.appsRow = appsRow;
        this.toolsLabel = toolsLabel;
        this.toolsRow = toolsRow;
        this.empty = empty;
        removeAllViews();
        for (View v : new View[]{clock, rail, hero, pills, appsLabel, appsRow,
                toolsLabel, toolsRow, empty}) {
            if (v != null) {
                addView(v);
            }
        }
    }

    /** The width the rows and the hero have to work in. */
    public static int contentWidthDp() {
        return Math.round(960f - CONTENT_X - RIGHT_MARGIN);
    }

    public void setNotice(String value) {
        this.notice = value;
        noticeWidth = value == null ? 0 : noticePaint.measureText(value);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int content = Dp.of(contentWidthDp());

        measureFree(clock, w, h);
        measureFree(rail, w, h);
        if (hero != null) {
            hero.measure(MeasureSpec.makeMeasureSpec(Dp.of(HERO_W), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        }
        measureFree(pills, w, h);
        measureFree(appsLabel, w, h);
        measureFree(toolsLabel, w, h);
        if (appsRow != null) {
            appsRow.measure(MeasureSpec.makeMeasureSpec(content, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Dp.of(AppCard.HEIGHT_DP),
                            MeasureSpec.EXACTLY));
        }
        if (toolsRow != null) {
            toolsRow.measure(MeasureSpec.makeMeasureSpec(content, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Dp.of(ToolChip.HEIGHT_DP + TOOLS_HEADROOM),
                            MeasureSpec.EXACTLY));
        }
        if (empty != null) {
            empty.measure(MeasureSpec.makeMeasureSpec(content, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        }
        setMeasuredDimension(w, h);
    }

    /** Let a view be whatever size it wants to be, inside the screen. */
    private void measureFree(View v, int w, int h) {
        if (v == null) {
            return;
        }
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = Dp.of(CONTENT_X);

        place(clock, Dp.of(CLOCK_X), Dp.of(CLOCK_TOP));
        place(rail, Dp.of(RAIL_X), Dp.of(RAIL_TOP));
        int heroTop = Dp.of(HERO_TOP);
        place(hero, x, heroTop);
        // The hero is as tall as the name and the line under it need, and the
        // name changes size to fit. So the actions are placed under whatever
        // the hero measured, never at a number that assumes a text size.
        int heroBottom = hero == null || hero.getVisibility() == GONE
                ? heroTop
                : heroTop + hero.getMeasuredHeight();
        place(pills, x, heroBottom + Dp.of(GAP_PILLS));

        if (appsLabel != null) {
            place(appsLabel, x, Dp.of(APPS_LABEL_BASE) - appsLabel.getMeasuredHeight());
        }
        place(appsRow, x, Dp.of(APPS_TOP - APPS_HEADROOM));
        if (toolsLabel != null) {
            place(toolsLabel, x, Dp.of(TOOLS_LABEL_BASE) - toolsLabel.getMeasuredHeight());
        }
        place(toolsRow, x, Dp.of(TOOLS_TOP - TOOLS_HEADROOM));

        if (empty != null) {
            place(empty, x, Dp.of(APPS_TOP));
        }
    }

    private void place(View v, int left, int top) {
        if (v == null || v.getVisibility() == GONE) {
            return;
        }
        v.layout(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
    }

    /**
     * The offline notice.
     *
     * It is the only warning the launcher has, so it is a line of type in the
     * corner rather than anything louder. Nothing on this screen needs the
     * network, so a dialogue would be wrong.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (notice == null) {
            return;
        }
        float right = getWidth() - Dp.f(RIGHT_MARGIN);
        canvas.drawText(notice, right - noticeWidth, Dp.f(46f), noticePaint);
    }
}
