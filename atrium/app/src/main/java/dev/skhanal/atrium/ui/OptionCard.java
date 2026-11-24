package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import dev.skhanal.atrium.content.TmdbImages;
import dev.skhanal.atrium.content.WatchOption;
import dev.skhanal.atrium.content.WatchText;

/**
 * One place to watch a title, on the detail screen: the service logo, its
 * name, how you pay, and what Select does.
 *
 * "Open" is green and "Get the app" is amber, and the words differ too, so
 * the difference never rests on colour alone.
 */
public final class OptionCard extends View implements Focusable {

    public static final float WIDTH_DP = 165f;
    public static final float HEIGHT_DP = 56f;
    private static final float LOGO_DP = 33f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint nameText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint smallText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF logoBox = new RectF();
    private WatchOption option;
    private String key;
    private Bitmap logo;
    private boolean focused;
    private Runnable action;

    public OptionCard(Context context) {
        super(context);
        setWillNotDraw(false);
        nameText.setTypeface(Type.bold());
        nameText.setTextSize(Dp.sp(11.5f));
        smallText.setTypeface(Type.medium());
        smallText.setTextSize(Dp.sp(9f));
    }

    public void bind(WatchOption w, Runnable onSelect) {
        option = w;
        action = onSelect;
        setContentDescription(w.app.name + ", " + WatchText.modes(w.modes) + ", " + WatchText.action(w));
        int s = Dp.of(LOGO_DP);
        String url = TmdbImages.logo(w.logo, s);
        key = url == null ? null : Images.key(url, s, s, null);
        logo = Images.get(getContext()).load(url, s, s, true, null, (k, b) -> {
            if (k.equals(key)) {
                logo = b;
                invalidate();
            }
        });
        invalidate();
    }

    @Override
    public void setFocused(boolean f) {
        focused = f;
        invalidate();
        Scale.to(this, f ? 1.05f : 1f);
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (option == null) {
            return;
        }
        float r = Dp.f(9f);
        box.set(0, 0, getWidth(), getHeight());
        fill.setColor(focused ? Colors.TEXT : Colors.SURFACE);
        canvas.drawRoundRect(box, r, r, fill);

        float pad = Dp.f(10f);
        float s = Dp.f(LOGO_DP);
        float top = (getHeight() - s) / 2f;
        RectF lb = logoBox;
        lb.set(pad, top, pad + s, top + s);
        if (logo != null) {
            canvas.drawBitmap(logo, null, lb, logoPaint);
        } else {
            fill.setColor(Colors.SURFACE_2);
            canvas.drawRoundRect(lb, Dp.f(7f), Dp.f(7f), fill);
        }

        float x = lb.right + Dp.f(9f);
        float y = Dp.f(9f) - nameText.ascent();
        nameText.setColor(focused ? Colors.INK : Colors.TEXT);
        canvas.drawText(option.app.name, x, y, nameText);
        y += Dp.f(13f);
        smallText.setColor(focused ? 0xB30A0D14 : Colors.TEXT_2);
        canvas.drawText(WatchText.modes(option.modes), x, y, smallText);
        y += Dp.f(12f);
        smallText.setColor(focused ? Colors.INK : option.installed ? Colors.OK : Colors.GET);
        canvas.drawText(WatchText.action(option), x, y, smallText);
    }
}
