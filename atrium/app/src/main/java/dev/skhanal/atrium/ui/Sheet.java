package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * A menu, drawn as words over the darkened screen.
 *
 * There is no card and no border. The artwork stays faintly visible behind a
 * scrim, so opening a menu feels like the lights going down rather than a new
 * screen arriving. It is also cheap to draw.
 */
public final class Sheet extends FrameLayout {

    public interface Callback {
        /** @param index the row chosen, in the order it was added */
        void onSheetChoice(int index);

        void onSheetClosed();
    }

    private static final float TITLE_SP = 13f;
    private static final float ROW_WIDTH_DP = 440f;
    /** Keeps the menu's title below the top bar, which the scrim only dims. */
    private static final float MARGIN_TOP_DP = 88f;
    private static final float MARGIN_BOTTOM_DP = 48f;

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint();
    private final LinearLayout column;
    private final ScrollView scroll;
    private final List<SheetRow> rows = new ArrayList<>();
    private Callback callback;
    private boolean open;
    private String title = "";
    private int titleX;
    private int titleBaselineOffset;

    public Sheet(Context context) {
        super(context);
        setFocusable(false);
        setWillNotDraw(false);
        // A lighter scrim looked better in isolation and was unusable on the
        // TV: the rows showed through and collided with the menu text. A menu
        // has to be readable first.
        scrimPaint.setColor(Colors.SCRIM);

        titlePaint.setTypeface(Type.semibold());
        titlePaint.setTextSize(Dp.sp(TITLE_SP));
        titlePaint.setColor(Colors.TEXT_3);
        titlePaint.setLetterSpacing(0.22f);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        // A long menu, such as Genres, does not fit on the screen. The scroll
        // follows focus on its own, and the faded edges show there is more.
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Dp.of(48f));
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LayoutParams lp = new LayoutParams(Dp.of(ROW_WIDTH_DP), LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        // The menu lines up with the hero, so opening one does not move the
        // eye anywhere it was not already looking.
        lp.leftMargin = Dp.of(HomeScreen.MARGIN_DP);
        lp.topMargin = Dp.of(MARGIN_TOP_DP);
        lp.bottomMargin = Dp.of(MARGIN_BOTTOM_DP);
        addView(scroll, lp);
    }

    public void configure(String title, List<String> labels, Callback callback) {
        this.title = title == null ? "" : title.toUpperCase(java.util.Locale.US);
        this.callback = callback;
        column.removeAllViews();
        rows.clear();
        scroll.scrollTo(0, 0);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            SheetRow row = new SheetRow(getContext());
            row.setText(labels.get(i));
            row.setOnFocusChangeListener((v, has) -> ((SheetRow) v).applyFocus(has));
            row.setOnClickListener(v -> {
                if (this.callback != null) {
                    this.callback.onSheetChoice(index);
                }
            });
            column.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            rows.add(row);
        }
        invalidate();
    }

    public SheetRow rowAt(int index) {
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    public int rowCount() {
        return rows.size();
    }

    /** Open, with focus on one row. */
    public void open(int focusIndex) {
        open = true;
        animate().cancel();
        setVisibility(VISIBLE);
        column.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        long d = Motion.d(Motion.SHEET);
        if (d == 0) {
            setAlpha(1f);
            scroll.setTranslationY(0f);
        } else {
            setAlpha(0f);
            scroll.setTranslationY(Dp.f(12f));
            animate().alpha(1f).setDuration(d).setInterpolator(Motion.SWIFT).start();
            scroll.animate().translationY(0f).setDuration(d)
                    .setInterpolator(Motion.SWIFT).start();
        }
        SheetRow row = rowAt(focusIndex);
        if (row == null) {
            for (SheetRow r : rows) {
                if (r.isFocusable()) {
                    row = r;
                    break;
                }
            }
        }
        if (row != null) {
            row.requestFocus();
        }
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        // Stop the rows holding focus straight away. The fade out still has to
        // run, and without this the menu would keep the focus it is giving up.
        column.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        column.clearFocus();
        long d = Motion.d(Motion.SHEET);
        if (d == 0) {
            setVisibility(GONE);
        } else {
            // One menu can close and another open in the same breath. By the
            // time this runs the menu may be open again, so the flag decides,
            // not the fact that a fade finished.
            animate().alpha(0f).setDuration(d).setInterpolator(Motion.SMOOTH)
                    .withEndAction(() -> {
                        if (!open) {
                            setVisibility(GONE);
                        }
                    }).start();
        }
        if (callback != null) {
            callback.onSheetClosed();
        }
    }

    /**
     * Tracked with a flag rather than read from the visibility, because the
     * menu stays visible while it fades out and must not still count as open.
     */
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_MENU)) {
            close();
            return true;
        }
        // Swallow Back's down event too, so it never reaches the activity and
        // closes the menu twice.
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        if (!title.isEmpty()) {
            float x = Dp.f(HomeScreen.MARGIN_DP) + Dp.f(23f);
            float y = scroll.getTop() - Dp.f(22f);
            canvas.drawText(title, x, y, titlePaint);
        }
    }
}
