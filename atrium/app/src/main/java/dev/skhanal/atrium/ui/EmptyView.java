package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;

/**
 * What the home screen shows when there are no apps to show.
 *
 * It is held to the same standard as the rest of the launcher: the same type,
 * the same margin, the same room behind it. An empty home screen should still
 * look like it was designed.
 */
public final class EmptyView extends View {

    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String title = "";
    private String body = "";
    private float titleBaseline;
    private float bodyBaseline;

    public EmptyView(Context context) {
        super(context);
        setFocusable(false);
        setWillNotDraw(false);
        setVisibility(GONE);
        titlePaint.setTypeface(Type.light());
        titlePaint.setTextSize(Dp.sp(33f));
        titlePaint.setColor(Palette.TEXT_PRIMARY);
        bodyPaint.setTypeface(Type.regular());
        bodyPaint.setTextSize(Dp.sp(16f));
        bodyPaint.setColor(Palette.TEXT_TERTIARY);
    }

    public void setText(String title, String body) {
        this.title = title;
        this.body = body;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics tm = titlePaint.getFontMetrics();
        Paint.FontMetrics bm = bodyPaint.getFontMetrics();
        titleBaseline = -tm.ascent;
        bodyBaseline = titleBaseline + tm.descent + Dp.f(18f) + -bm.ascent;
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                (int) Math.ceil(bodyBaseline + bm.descent));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawText(title, 0, titleBaseline, titlePaint);
        canvas.drawText(body, 0, bodyBaseline, bodyPaint);
    }
}
