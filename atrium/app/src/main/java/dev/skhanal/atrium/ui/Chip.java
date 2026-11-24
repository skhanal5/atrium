package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A word you can press: a button, or an item in the top bar.
 *
 * Focused, it turns white with ink text, so focus reads from across a room by
 * brightness alone and not by colour. A top-bar item that is the current
 * screen also has a short line under it.
 */
public final class Chip extends View implements Focusable {

    public enum Style { BUTTON, NAV }

    public enum Icon { NONE, PLAY, INFO }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path play = new Path();
    private final Style style;
    private String label = "";
    private Icon icon = Icon.NONE;
    private boolean focused;
    private boolean current;
    private boolean enabled = true;
    private Runnable action;

    public Chip(Context context, Style style) {
        super(context);
        this.style = style;
        setWillNotDraw(false);
        text.setTypeface(style == Style.BUTTON ? Type.bold() : Type.semibold());
        text.setTextSize(Dp.sp(style == Style.BUTTON ? 12.5f : 13f));
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(Dp.f(1.4f));
        mark.setStrokeCap(Paint.Cap.ROUND);
    }

    public Chip set(String label, Icon icon, Runnable action) {
        this.label = label;
        this.icon = icon;
        this.action = action;
        setContentDescription(label);
        requestLayout();
        invalidate();
        return this;
    }

    /** For a top-bar item: whether it is the screen you are on. */
    public void setCurrent(boolean current) {
        this.current = current;
        invalidate();
    }

    /** A disabled button still takes focus, so the row keeps its shape, but looks faded. */
    public void setChipEnabled(boolean on) {
        enabled = on;
        setAlpha(on ? 1f : 0.4f);
    }

    @Override
    public void setFocused(boolean f) {
        focused = f;
        invalidate();
        Scale.to(this, f && style == Style.BUTTON ? 1.05f : 1f);
    }

    @Override
    public void activate() {
        if (enabled && action != null) {
            action.run();
        }
    }

    private float iconSize() {
        return icon == Icon.NONE ? 0 : Dp.f(11f);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float padH = Dp.f(style == Style.BUTTON ? 17f : 14f);
        float gap = icon == Icon.NONE ? 0 : Dp.f(7f);
        int w = (int) Math.ceil(padH * 2 + iconSize() + gap + text.measureText(label));
        int h = Dp.of(style == Style.BUTTON ? 34f : 30f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int fg = focused ? Colors.INK : (style == Style.NAV && !current ? Colors.TEXT_2 : Colors.TEXT);
        box.set(0, 0, getWidth(), getHeight());
        if (focused) {
            fill.setColor(Colors.TEXT);
            canvas.drawRoundRect(box, radius(), radius(), fill);
        } else if (style == Style.BUTTON) {
            fill.setColor(Colors.SURFACE);
            canvas.drawRoundRect(box, radius(), radius(), fill);
        }
        float padH = Dp.f(style == Style.BUTTON ? 17f : 14f);
        float x = padH;
        float cy = getHeight() / 2f;
        if (icon != Icon.NONE) {
            drawIcon(canvas, x, cy, fg);
            x += iconSize() + Dp.f(7f);
        }
        text.setColor(fg);
        Paint.FontMetrics fm = text.getFontMetrics();
        canvas.drawText(label, x, cy - (fm.ascent + fm.descent) / 2f, text);
        if (style == Style.NAV && current && !focused) {
            mark.setColor(Colors.TEXT);
            float y = getHeight() - Dp.f(2f);
            canvas.drawLine(padH, y, getWidth() - padH, y, mark);
        }
    }

    private float radius() {
        return style == Style.BUTTON ? Dp.f(7f) : getHeight() / 2f;
    }

    private void drawIcon(Canvas canvas, float x, float cy, int color) {
        float s = iconSize();
        fill.setColor(color);
        if (icon == Icon.PLAY) {
            play.reset();
            play.moveTo(x + s * 0.15f, cy - s / 2f);
            play.lineTo(x + s, cy);
            play.lineTo(x + s * 0.15f, cy + s / 2f);
            play.close();
            canvas.drawPath(play, fill);
            return;
        }
        // An "i" in a circle.
        mark.setColor(color);
        canvas.drawCircle(x + s / 2f, cy, s / 2f - mark.getStrokeWidth() / 2f, mark);
        canvas.drawLine(x + s / 2f, cy - s * 0.05f, x + s / 2f, cy + s * 0.25f, mark);
        canvas.drawCircle(x + s / 2f, cy - s * 0.25f, mark.getStrokeWidth() * 0.6f, fill);
    }
}
