package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/** The last tile in the Your apps row. It opens the Get apps screen. */
public final class GetAppsTile extends View implements Focusable {

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    /** Dashed at rest, so it reads as a place to add something. Solid when focused. */
    private final DashPathEffect dash;
    private final Runnable action;
    private boolean focused;

    public GetAppsTile(Context context, Runnable onSelect) {
        super(context);
        action = onSelect;
        setWillNotDraw(false);
        setContentDescription("Get apps");
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(Dp.f(1.5f));
        dash = new DashPathEffect(new float[] {Dp.f(5f), Dp.f(4f)}, 0);
        plus.setStrokeWidth(Dp.f(2.4f));
        plus.setStrokeCap(Paint.Cap.ROUND);
        name.setTextSize(Dp.sp(10f));
        name.setTextAlign(Paint.Align.CENTER);
        name.setTypeface(Type.medium());
    }

    @Override
    public void setFocused(boolean f) {
        focused = f;
        invalidate();
        Scale.to(this, f ? 1.08f : 1f);
    }

    @Override
    public void activate() {
        action.run();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Dp.of(AppTile.WIDTH_DP), Dp.of(AppTile.HEIGHT_DP));
        setPivotX(Dp.f(AppTile.WIDTH_DP) / 2f);
        setPivotY(Dp.f(AppTile.PLATE_H_DP) / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Dp.f(9f);
        float inset = outline.getStrokeWidth();
        box.set(inset, inset, getWidth() - inset, Dp.f(AppTile.PLATE_H_DP) - inset);
        outline.setColor(focused ? Colors.TEXT : Colors.SURFACE_2);
        outline.setPathEffect(focused ? null : dash);
        canvas.drawRoundRect(box, r, r, outline);
        plus.setColor(Colors.TEXT);
        float cx = box.centerX();
        float cy = box.centerY();
        float arm = Dp.f(9f);
        canvas.drawLine(cx - arm, cy, cx + arm, cy, plus);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, plus);
        name.setColor(focused ? Colors.TEXT : Colors.TEXT_2);
        canvas.drawText("Get apps", getWidth() / 2f, Dp.f(AppTile.PLATE_H_DP + 6f) - name.ascent(), name);
    }
}
