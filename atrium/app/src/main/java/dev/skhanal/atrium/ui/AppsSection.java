package dev.skhanal.atrium.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import java.util.List;

import dev.skhanal.atrium.data.AppEntry;

/**
 * Every app, as a grid.
 *
 * The row on the home screen is the apps in the order the user put them. This
 * is all of them at once, for the day the row is longer than the screen. It
 * uses the same tiles, the same focus language and the same margins, so moving
 * between the two feels like one product rather than two screens.
 *
 * The rail stays visible on the left while this is open, so the way back is
 * always in sight.
 */
public final class AppsSection extends FrameLayout {

    /** Space between rows, measured between the tiles themselves. */
    private static final float ROW_GAP_DP = 26f;

    public interface Listener {
        void onAppFocused(AppCard card);

        void onAppChosen(AppCard card);

        void onAppMenu(AppCard card);
    }

    private final Label header;
    private final ScrollView scroll;
    private final Grid grid;
    private Listener listener;

    public AppsSection(Context context) {
        super(context);
        setFocusable(false);

        header = new Label(context);
        addView(header, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        grid = new Grid(context);
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setHeader(String text) {
        header.setText(text);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** Build the tiles. The same AppCard the home row uses. */
    public void bind(List<AppEntry> apps) {
        grid.removeAllViews();
        for (AppEntry e : apps) {
            final AppCard card = new AppCard(getContext());
            card.bind(e);
            card.setId(View.generateViewId());
            card.setOnFocusChangeListener((v, has) -> {
                AppCard c = (AppCard) v;
                c.applyFocus(has);
                if (has && listener != null) {
                    listener.onAppFocused(c);
                }
            });
            card.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppChosen((AppCard) v);
                }
            });
            card.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onAppMenu((AppCard) v);
                }
                return true;
            });
            grid.addView(card);
        }
        grid.requestLayout();
    }

    public View firstCard() {
        return grid.getChildCount() == 0 ? null : grid.getChildAt(0);
    }

    /** The card showing this package, so focus can land where it left off. */
    public View cardFor(String pkg) {
        for (int i = 0; i < grid.getChildCount(); i++) {
            AppCard c = (AppCard) grid.getChildAt(i);
            if (c.entry() != null && c.entry().pkg.equals(pkg)) {
                return c;
            }
        }
        return firstCard();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int headerH = header.getMeasuredHeight();
        header.layout(0, 0, header.getMeasuredWidth(), headerH);
        int top = headerH + Dp.of(22f);
        scroll.layout(0, top, r - l, b - t);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        measureChild(header, widthSpec, heightSpec);
    }

    /**
     * Tiles laid out left to right and wrapped, with as many columns as fit.
     *
     * A grid rather than a list because app icons are recognised by shape and
     * colour, and a grid lets the eye sweep rather than read.
     */
    private static final class Grid extends ViewGroup {

        Grid(Context context) {
            super(context);
        }

        private int columns(int width) {
            int cell = Dp.of(AppCard.WIDTH_DP);
            int gap = Dp.of(AppCard.GAP_DP);
            int cols = Math.max(1, (width + gap) / (cell + gap));
            return cols;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int n = getChildCount();
            int cols = columns(width);
            int rows = (n + cols - 1) / Math.max(1, cols);
            int rowH = Dp.of(AppCard.HEIGHT_DP + ROW_GAP_DP);

            for (int i = 0; i < n; i++) {
                getChildAt(i).measure(
                        MeasureSpec.makeMeasureSpec(Dp.of(AppCard.WIDTH_DP), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Dp.of(AppCard.HEIGHT_DP), MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width, Math.max(0, rows * rowH));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int cols = columns(width);
            int cell = Dp.of(AppCard.WIDTH_DP);
            int gap = Dp.of(AppCard.GAP_DP);
            int rowH = Dp.of(AppCard.HEIGHT_DP + ROW_GAP_DP);

            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int col = i % cols;
                int row = i / cols;
                int x = col * (cell + gap);
                int y = row * rowH;
                v.layout(x, y, x + cell, y + Dp.of(AppCard.HEIGHT_DP));
            }
        }
    }
}
