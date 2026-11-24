package dev.skhanal.atrium.ui;

import android.content.Context;
import android.widget.HorizontalScrollView;

/** The horizontal app row. Each card draws fully inside its own bounds. */
public final class RowScroller extends HorizontalScrollView {

    public RowScroller(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setFocusable(false);
    }
}
