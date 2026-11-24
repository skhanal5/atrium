package dev.skhanal.atrium.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The small marks used by the rail and the tools row.
 *
 * They are drawn as paths rather than shipped as images for two reasons. They
 * stay sharp at any size on a panel that scales everything, and they can take
 * the colour of whatever is focused without a second copy of each file.
 *
 * Every mark is drawn inside a square of the given size, centred on the point
 * given, in one consistent line weight. One drawing hand across the whole
 * launcher.
 */
public final class Glyph {

    public static final int HOME = 0;
    public static final int APPS = 1;
    public static final int SETTINGS = 2;
    public static final int WIFI = 3;
    public static final int DISPLAY = 4;
    public static final int TV = 5;
    public static final int ATRIUM = 6;

    private static final Path path = new Path();
    private static final RectF rect = new RectF();

    private Glyph() {
    }

    /**
     * @param size the width and height of the square the mark sits in
     */
    public static void draw(Canvas canvas, int kind, float cx, float cy, float size, Paint p) {
        float h = size / 2f;
        float stroke = Math.max(Dp.f(1.6f), size * 0.085f);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStyle(Paint.Style.STROKE);

        switch (kind) {
            case HOME:
                path.reset();
                path.moveTo(cx - h, cy - size * 0.03f);
                path.lineTo(cx, cy - h);
                path.lineTo(cx + h, cy - size * 0.03f);
                path.moveTo(cx - h * 0.76f, cy - size * 0.03f);
                path.lineTo(cx - h * 0.76f, cy + h);
                path.lineTo(cx + h * 0.76f, cy + h);
                path.lineTo(cx + h * 0.76f, cy - size * 0.03f);
                canvas.drawPath(path, p);
                break;

            case APPS: {
                // Four rounded squares. The mark for "everything you have".
                float s = size * 0.38f;
                float gap = size * 0.14f;
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 4; i++) {
                    float x = cx + ((i % 2 == 0) ? -(s + gap) / 2f - s / 2f : (s + gap) / 2f - s / 2f);
                    float y = cy + ((i < 2) ? -(s + gap) / 2f - s / 2f : (s + gap) / 2f - s / 2f);
                    rect.set(x, y, x + s, y + s);
                    canvas.drawRoundRect(rect, s * 0.3f, s * 0.3f, p);
                }
                break;
            }

            case SETTINGS: {
                // Two sliders. A cogwheel at this size turns into a blob, and
                // sliders say "things you can set" just as plainly.
                float r = size * 0.13f;
                float y1 = cy - size * 0.22f;
                float y2 = cy + size * 0.22f;
                canvas.drawLine(cx - h, y1, cx + h, y1, p);
                canvas.drawLine(cx - h, y2, cx + h, y2, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx + size * 0.16f, y1, r, p);
                canvas.drawCircle(cx - size * 0.16f, y2, r, p);
                break;
            }

            case WIFI: {
                for (int i = 0; i < 3; i++) {
                    float r = size * (0.20f + i * 0.22f);
                    rect.set(cx - r, cy + size * 0.28f - r, cx + r, cy + size * 0.28f + r);
                    canvas.drawArc(rect, 215f, 110f, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy + size * 0.30f, stroke * 0.8f, p);
                break;
            }

            case DISPLAY:
                rect.set(cx - h, cy - h * 0.82f, cx + h, cy + h * 0.30f);
                canvas.drawRoundRect(rect, size * 0.10f, size * 0.10f, p);
                canvas.drawLine(cx, cy + h * 0.30f, cx, cy + h * 0.66f, p);
                canvas.drawLine(cx - h * 0.46f, cy + h * 0.66f, cx + h * 0.46f, cy + h * 0.66f, p);
                break;

            case TV:
                rect.set(cx - h, cy - h * 0.58f, cx + h, cy + h * 0.72f);
                canvas.drawRoundRect(rect, size * 0.10f, size * 0.10f, p);
                canvas.drawLine(cx - h * 0.44f, cy - h, cx, cy - h * 0.58f, p);
                canvas.drawLine(cx + h * 0.44f, cy - h, cx, cy - h * 0.58f, p);
                break;

            default:
                // Atrium's own mark: an opening with light coming through it.
                rect.set(cx - h * 0.72f, cy - h, cx + h * 0.72f, cy + h);
                canvas.drawRoundRect(rect, size * 0.36f, size * 0.36f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - h * 0.18f, size * 0.12f, p);
                break;
        }
        p.setStyle(Paint.Style.FILL);
    }
}
