package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import dev.skhanal.atrium.content.TmdbImages;
import dev.skhanal.atrium.content.TvApp;

/**
 * One app on the Get apps screen: its logo, its name, and whether it is
 * installed. The last card on the screen is the way into the full Appstore.
 */
public final class CatalogCard extends View implements Focusable {

    public static final float WIDTH_DP = 114f;
    public static final float HEIGHT_DP = 72f;
    private static final float LOGO_DP = 30f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nameText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint smallText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint initialText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF logoBox = new RectF();
    private String name = "";
    private String initial = "";
    private boolean installed;
    private boolean appstore;
    private String key;
    private Bitmap logo;
    private boolean focused;
    private Runnable action;

    public CatalogCard(Context context) {
        super(context);
        setWillNotDraw(false);
        nameText.setTypeface(Type.semibold());
        nameText.setTextSize(Dp.sp(10f));
        nameText.setTextAlign(Paint.Align.CENTER);
        smallText.setTypeface(Type.bold());
        smallText.setTextSize(Dp.sp(7.5f));
        smallText.setLetterSpacing(0.08f);
        initialText.setTypeface(Type.semibold());
        initialText.setTextSize(Dp.sp(17f));
        initialText.setTextAlign(Paint.Align.CENTER);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(Dp.f(2.2f));
        mark.setStrokeCap(Paint.Cap.ROUND);
    }

    /** An app. Select opens it when installed, else its Appstore page. */
    public void bind(TvApp app, boolean isInstalled, Runnable onSelect) {
        name = app.name;
        initial = app.name.substring(0, 1);
        installed = isInstalled;
        appstore = false;
        action = onSelect;
        setContentDescription(app.name + (isInstalled ? ", installed" : ""));
        int s = Dp.of(LOGO_DP);
        String url = TmdbImages.logo(app.logo, s);
        key = url == null ? null : Images.key(url, s, s, null);
        logo = Images.get(getContext()).load(url, s, s, true, null, (k, b) -> {
            if (k.equals(key)) {
                logo = b;
                invalidate();
            }
        });
        invalidate();
    }

    /** The last card: every app, through the Appstore's own search. */
    public void bindAppstore(Runnable onSelect) {
        name = "Search the full Appstore";
        appstore = true;
        installed = false;
        logo = null;
        key = null;
        action = onSelect;
        setContentDescription(name);
        invalidate();
    }

    @Override
    public void setFocused(boolean f) {
        focused = f;
        invalidate();
        Scale.to(this, f ? 1.07f : 1f);
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
        float r = Dp.f(10f);
        box.set(0, 0, getWidth(), getHeight());
        fill.setColor(focused ? Colors.TEXT : Colors.SURFACE);
        canvas.drawRoundRect(box, r, r, fill);
        int fg = focused ? Colors.INK : Colors.TEXT;

        float s = Dp.f(LOGO_DP);
        float x = (getWidth() - s) / 2f;
        float y = Dp.f(10f);
        logoBox.set(x, y, x + s, y + s);
        if (appstore) {
            // A magnifying glass, for search.
            mark.setColor(fg);
            float cx = logoBox.centerX() - s * 0.08f;
            float cy = logoBox.centerY() - s * 0.08f;
            canvas.drawCircle(cx, cy, s * 0.26f, mark);
            canvas.drawLine(cx + s * 0.19f, cy + s * 0.19f, cx + s * 0.38f, cy + s * 0.38f, mark);
        } else if (logo != null) {
            canvas.drawBitmap(logo, null, logoBox, logoPaint);
        } else {
            fill.setColor(focused ? 0x260A0D14 : Colors.SURFACE_2);
            canvas.drawRoundRect(logoBox, Dp.f(8f), Dp.f(8f), fill);
            initialText.setColor(fg);
            canvas.drawText(initial, logoBox.centerX(),
                    logoBox.centerY() - (initialText.ascent() + initialText.descent()) / 2f, initialText);
        }

        nameText.setColor(fg);
        String shown = TextUtils.ellipsize(name, nameText, getWidth() - Dp.f(12f),
                TextUtils.TruncateAt.END).toString();
        canvas.drawText(shown, getWidth() / 2f, logoBox.bottom + Dp.f(6f) - nameText.ascent(), nameText);

        if (installed) {
            smallText.setColor(focused ? 0xFF1E7A3B : Colors.OK);
            String label = "INSTALLED";
            canvas.drawText(label, getWidth() - Dp.f(8f) - smallText.measureText(label),
                    Dp.f(7f) - smallText.ascent(), smallText);
        }
    }
}
