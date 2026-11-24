package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import dev.skhanal.atrium.content.Title;
import dev.skhanal.atrium.content.TmdbImages;

/**
 * A movie or show in a row: its picture with rounded corners, and its name.
 *
 * Wide cards use the backdrop. Poster cards use the poster. The rounding is
 * drawn with a bitmap shader, not a clip path, because a clip path costs this
 * GPU an extra layer per card.
 */
public final class TitleCard extends View implements Focusable {

    public static final float WIDE_W_DP = 200f;
    public static final float WIDE_H_DP = 112.5f;
    public static final float POSTER_W_DP = 98f;
    public static final float POSTER_H_DP = 147f;
    private static final float NAME_GAP_DP = 6f;
    private static final float BADGE_DP = 22f;

    private final boolean poster;
    private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final RectF badgeBox = new RectF();
    private Title title;
    private String shown = "";
    private String key;
    private String badgeKey;
    private Bitmap badge;
    private boolean focused;
    private Runnable action;

    public TitleCard(Context context, boolean poster) {
        super(context);
        this.poster = poster;
        setWillNotDraw(false);
        empty.setColor(Colors.SURFACE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Dp.f(2f));
        ring.setColor(Colors.TEXT);
        name.setTextSize(Dp.sp(10.5f));
        name.setTypeface(Type.semibold());
    }

    /**
     * @param badgeLogo a TMDB logo path for the app you can watch this in, or null
     */
    public void bind(Title t, String badgeLogo, Runnable onSelect) {
        title = t;
        action = onSelect;
        setContentDescription(t.name);
        name.setTypeface(Type.forLabel(t.name, Type.semibold()));
        shown = TextUtils.ellipsize(t.name, name, picW(), TextUtils.TruncateAt.END).toString();
        pic.setShader(null);
        int w = Math.round(picW());
        int h = Math.round(picH());
        String url = poster ? TmdbImages.poster(t.poster != null ? t.poster : t.backdrop, w)
                : TmdbImages.backdrop(t.backdrop != null ? t.backdrop : t.poster, w);
        key = url == null ? null : Images.key(url, w, h, null);
        Images images = Images.get(getContext());
        setPicture(images.load(url, w, h, true, null, this::onPicture));
        badge = null;
        int bs = Dp.of(BADGE_DP);
        String badgeUrl = poster ? null : TmdbImages.logo(badgeLogo, bs);
        badgeKey = badgeUrl == null ? null : Images.key(badgeUrl, bs, bs, null);
        Bitmap b = images.load(badgeUrl, bs, bs, true, null, this::onPicture);
        if (b != null) {
            badge = b;
        }
        invalidate();
    }

    public Title title() {
        return title;
    }

    /** A picture arrives later. Only take it if this card still shows that title. */
    private void onPicture(String k, Bitmap b) {
        if (k.equals(key)) {
            setPicture(b);
        } else if (k.equals(badgeKey)) {
            badge = b;
            invalidate();
        }
    }

    private void setPicture(Bitmap b) {
        if (b == null) {
            return;
        }
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(picW() / b.getWidth(), picH() / b.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((picW() - b.getWidth() * scale) / 2f, (picH() - b.getHeight() * scale) / 2f);
        s.setLocalMatrix(m);
        pic.setShader(s);
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

    private float picW() {
        return Dp.f(poster ? POSTER_W_DP : WIDE_W_DP);
    }

    private float picH() {
        return Dp.f(poster ? POSTER_H_DP : WIDE_H_DP);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(picW()), Math.round(picH() + Dp.f(NAME_GAP_DP + 16f)));
        setPivotX(picW() / 2f);
        setPivotY(picH() / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Dp.f(8f);
        box.set(0, 0, picW(), picH());
        canvas.drawRoundRect(box, r, r, pic.getShader() != null ? pic : empty);
        if (badge != null) {
            float s = Dp.f(BADGE_DP);
            float m = Dp.f(6f);
            badgeBox.set(m, m, m + s, m + s);
            canvas.drawBitmap(badge, null, badgeBox, badgePaint);
        }
        if (focused) {
            float inset = ring.getStrokeWidth() / 2f;
            box.inset(inset, inset);
            canvas.drawRoundRect(box, r, r, ring);
        }
        name.setColor(focused ? Colors.TEXT : Colors.TEXT_2);
        canvas.drawText(shown, 0, picH() + Dp.f(NAME_GAP_DP) - name.ascent(), name);
    }
}
