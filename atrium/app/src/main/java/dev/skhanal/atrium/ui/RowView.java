package dev.skhanal.atrium.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One row on the home screen: a heading, then items side by side.
 *
 * When the items are wider than the screen, the row slides so the focused item
 * sits at the left edge, the way TV home screens behave. Only the row's
 * translation changes, so sliding never redraws the cards.
 */
public final class RowView extends LinearLayout {

    static final float GAP_DP = 13f;

    private final LinearLayout track;
    private final List<View> items = new ArrayList<>();

    public RowView(Context context, String heading, String note) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(Dp.of(HomeScreen.MARGIN_DP), 0, Dp.of(HomeScreen.MARGIN_DP), Dp.of(18f));

        TextView h = new TextView(context);
        h.setTypeface(Type.semibold());
        h.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Dp.sp(12.5f));
        h.setTextColor(Colors.TEXT);
        h.setIncludeFontPadding(false);
        SpannableStringBuilder sb = new SpannableStringBuilder(heading);
        if (note != null) {
            int start = sb.length();
            sb.append("   ").append(note);
            sb.setSpan(new ForegroundColorSpan(Colors.TEXT_3), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.8f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        h.setText(sb);
        LayoutParams hp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = Dp.of(10f);
        addView(h, hp);

        track = new LinearLayout(context);
        track.setOrientation(HORIZONTAL);
        track.setClipChildren(false);
        addView(track, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void add(View item) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (!items.isEmpty()) {
            lp.setMarginStart(Dp.of(GAP_DP));
        }
        track.addView(item, lp);
        items.add(item);
    }

    public List<View> items() {
        return items;
    }

    /** Slide so this item is in view, at the left edge once the row has to move. */
    public void reveal(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        int visible = getWidth() - getPaddingLeft() - getPaddingRight();
        // The track is measured no wider than the screen, so its own width
        // never shows the overflow. The last item's right edge does.
        int contentWidth = items.get(items.size() - 1).getRight();
        int max = Math.max(0, contentWidth - visible);
        float x = -Math.min(max, items.get(index).getLeft());
        track.animate().cancel();
        long d = Motion.d(Motion.SECTION);
        if (d == 0) {
            track.setTranslationX(x);
        } else {
            track.animate().translationX(x).setDuration(d).setInterpolator(Motion.SWIFT).start();
        }
    }

    /** Swap two items, for moving an app along the row. */
    public void swap(int from, int to) {
        View v = items.remove(from);
        items.add(to, v);
        track.removeView(v);
        track.addView(v, to);
        for (int i = 0; i < items.size(); i++) {
            LayoutParams lp = (LayoutParams) items.get(i).getLayoutParams();
            lp.setMarginStart(i == 0 ? 0 : Dp.of(GAP_DP));
            items.get(i).setLayoutParams(lp);
        }
    }
}
