package dev.skhanal.atrium.ui;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The line across the top: where you can go, an offline notice when needed, and the time. */
public final class TopBar extends LinearLayout {

    private final List<Chip> items = new ArrayList<>();
    private final TextView notice;
    private final TextView clock;
    private boolean use24;

    public TopBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);

        View spacer = new View(context);
        addView(spacer, new LayoutParams(0, 1, 1f));

        notice = new TextView(context);
        notice.setTypeface(Type.semibold());
        notice.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(11f));
        notice.setTextColor(Colors.WARN);
        notice.setVisibility(GONE);
        LayoutParams np = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        np.setMarginEnd(Dp.of(16f));
        addView(notice, np);

        clock = new TextView(context);
        clock.setTypeface(Type.medium());
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(15f));
        clock.setTextColor(Colors.TEXT_2);
        addView(clock);
    }

    /** Add a destination, left to right. */
    public Chip addItem(String label, Runnable action) {
        Chip c = new Chip(getContext(), Chip.Style.NAV);
        c.set(label, Chip.Icon.NONE, action);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (!items.isEmpty()) {
            lp.setMarginStart(Dp.of(4f));
        }
        addView(c, items.size(), lp);
        items.add(c);
        return c;
    }

    public List<Chip> items() {
        return items;
    }

    public void setUse24(boolean on) {
        use24 = on;
        tick();
    }

    /** Called once a minute. */
    public void tick() {
        clock.setText(DateFormat.format(use24 ? "H:mm" : "h:mm a", new Date()));
    }

    /** A short warning, such as "Offline", or null for none. */
    public void setNotice(String text) {
        notice.setText(text);
        notice.setVisibility(text == null ? GONE : VISIBLE);
    }
}
