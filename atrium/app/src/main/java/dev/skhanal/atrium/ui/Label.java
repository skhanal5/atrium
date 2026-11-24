package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * A section heading, such as "Your apps".
 *
 * Small, upper case and widely letter spaced, against large light type in the
 * hero. The two opposite textures are what make the pairing look deliberate
 * rather than merely big and small.
 */
public final class Label extends View {

    private static final float SIZE_SP = 12f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "";

    public Label(Context context) {
        super(context);
        setFocusable(false);
        setWillNotDraw(false);
        paint.setTypeface(Type.medium());
        paint.setTextSize(Dp.sp(SIZE_SP));
        paint.setLetterSpacing(0.26f);
        paint.setColor(Palette.TEXT_TERTIARY);
    }

    public void setText(String value) {
        this.text = value == null ? "" : value.toUpperCase(java.util.Locale.getDefault());
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        setMeasuredDimension(Math.round(paint.measureText(text)),
                Math.round(-fm.ascent + fm.descent));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawText(text, 0, -paint.getFontMetrics().ascent, paint);
    }
}
