package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import dev.skhanal.atrium.data.AppEntry;

/**
 * One app in the Your apps row: its icon on a plate of its own colour, and its
 * name underneath.
 *
 * The plate colour is read from the app's icon, darkened so the icon stands
 * out. An icon with no colour sits on a neutral plate.
 */
public final class AppTile extends View implements Focusable {

    public static final float WIDTH_DP = 108f;
    public static final float PLATE_H_DP = 61f;
    public static final float ICON_DP = 39f;
    public static final float HEIGHT_DP = PLATE_H_DP + 6f + 14f;

    private static final int NEUTRAL_PLATE = 0xFF1C2030;

    private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF iconBox = new RectF();
    private AppEntry entry;
    private String shown = "";
    private boolean focused;
    private boolean moving;
    private Runnable action;

    public AppTile(Context context) {
        super(context);
        setWillNotDraw(false);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Dp.f(2f));
        name.setTextSize(Dp.sp(10f));
        name.setTextAlign(Paint.Align.CENTER);
    }

    public void bind(AppEntry e, Runnable onSelect) {
        entry = e;
        action = onSelect;
        plate.setColor(plateFor(e.accent));
        name.setTypeface(e.typeface != null ? e.typeface : Type.medium());
        shown = TextUtils.ellipsize(e.label, name, Dp.f(WIDTH_DP), TextUtils.TruncateAt.END).toString();
        setContentDescription(e.label);
        invalidate();
    }

    public AppEntry entry() {
        return entry;
    }

    /** While the app is being moved along the row its ring turns amber. */
    public void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    public void setFocused(boolean f) {
        focused = f;
        invalidate();
        Scale.to(this, f ? 1.08f : 1f);
    }

    @Override
    public void activate() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Dp.of(WIDTH_DP), Dp.of(HEIGHT_DP));
        // Grow around the plate, so the name below does not push into the next row.
        setPivotX(Dp.f(WIDTH_DP) / 2f);
        setPivotY(Dp.f(PLATE_H_DP) / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Dp.f(9f);
        box.set(0, 0, getWidth(), Dp.f(PLATE_H_DP));
        canvas.drawRoundRect(box, r, r, plate);
        if (entry != null && entry.icon != null) {
            float s = Dp.f(ICON_DP);
            float x = (getWidth() - s) / 2f;
            float y = (box.height() - s) / 2f;
            iconBox.set(x, y, x + s, y + s);
            canvas.drawBitmap(entry.icon, null, iconBox, icon);
        }
        if (focused || moving) {
            ring.setColor(moving ? Colors.GET : Colors.TEXT);
            float inset = ring.getStrokeWidth() / 2f;
            box.inset(inset, inset);
            canvas.drawRoundRect(box, r, r, ring);
        }
        name.setColor(focused ? Colors.TEXT : Colors.TEXT_2);
        canvas.drawText(shown, getWidth() / 2f, Dp.f(PLATE_H_DP + 6f) - name.ascent(), name);
    }

    /** The icon's own colour, darkened and calmed so the icon stands out on it. */
    private static int plateFor(int accent) {
        if (accent == 0) {
            return NEUTRAL_PLATE;
        }
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(hsv[1], 0.7f);
        hsv[2] = 0.34f;
        return Color.HSVToColor(hsv);
    }
}
