package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * The time and date, set as a masthead.
 *
 * Three small decisions do most of the work here:
 *
 * - The separator is drawn dimmer than the digits. At this size a full-strength
 *   colon splits the number in half. Holding it back lets the time read as one
 *   object.
 * - The date is small, upper case and widely letter spaced, which is the
 *   opposite texture to the large light numerals. The contrast is what makes
 *   the pairing look deliberate rather than merely big and small.
 * - Both lines are aligned on the left edge of the ink, not the left edge of
 *   the text box, so the column of type is optically straight.
 */
public final class ClockView extends View {

    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String hours = "";
    private String separator = ":";
    private String minutes = "";
    private String suffix = "";
    private String date = "";

    private boolean use24 = false;

    private float timeBaseline;
    private float dateBaseline;
    private float hoursWidth;
    private float sepWidth;
    private float minutesWidth;
    private float timeBearing;
    private float dateBearing;
    private final Rect inkBounds = new Rect();

    // The clock is no longer the masthead of the screen. The hero is. So it
    // sits above the rail at the size of a label, not a headline, and the
    // hierarchy of the page reads in the right order from across the room.
    private static final float TIME_SP = 34f;
    private static final float SUFFIX_SP = 13f;
    private static final float DATE_SP = 11f;
    private static final float GAP_DP = 9f;

    public ClockView(Context context) {
        super(context);
        setFocusable(false);
        setWillNotDraw(false);

        timePaint.setTypeface(Type.light());
        timePaint.setTextSize(Dp.sp(TIME_SP));
        timePaint.setColor(Palette.TEXT_PRIMARY);
        // Large light numerals sit better with a little air between them.
        timePaint.setLetterSpacing(0.005f);

        sepPaint.set(timePaint);
        sepPaint.setColor(Palette.TEXT_FAINT);

        datePaint.setTypeface(Type.medium());
        datePaint.setTextSize(Dp.sp(DATE_SP));
        datePaint.setColor(Palette.TEXT_TERTIARY);
        datePaint.setLetterSpacing(0.20f);

        update();
    }

    public void setUse24(boolean on) {
        if (use24 != on) {
            use24 = on;
            update();
            requestLayout();
            invalidate();
        }
    }

    /** Re-read the clock. Called on the minute and whenever the screen resumes. */
    public void update() {
        Calendar c = Calendar.getInstance();
        int h = use24 ? c.get(Calendar.HOUR_OF_DAY) : c.get(Calendar.HOUR);
        if (!use24 && h == 0) {
            h = 12;
        }
        hours = use24 ? String.format(Locale.getDefault(), "%02d", h) : String.valueOf(h);
        minutes = String.format(Locale.getDefault(), "%02d", c.get(Calendar.MINUTE));
        suffix = use24 ? "" : (c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");

        String weekday = c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
        String month = c.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        int day = c.get(Calendar.DAY_OF_MONTH);
        if (weekday == null || month == null) {
            date = "";
        } else {
            date = (weekday + "  ·  " + day + " " + month).toUpperCase(Locale.getDefault());
        }

        measureText();
    }

    /** The hour, used to pick the light preset. */
    public int hourOfDay() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    private void measureText() {
        hoursWidth = timePaint.measureText(hours);
        sepWidth = sepPaint.measureText(separator);
        minutesWidth = timePaint.measureText(minutes);
        // Every glyph carries a side bearing, and it is a different amount for
        // the big numerals and the small date. Drawing each line shifted by its
        // own bearing puts the ink on the margin, so the two lines line up by
        // eye instead of by text box.
        timeBearing = inkLeft(timePaint, hours);
        dateBearing = inkLeft(datePaint, date);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics tm = timePaint.getFontMetrics();
        Paint.FontMetrics dm = datePaint.getFontMetrics();

        // Cap height rather than the full line box, so the gap above the
        // numerals is the gap we chose and not whatever the font declares.
        timeBaseline = -tm.ascent * 0.82f;
        dateBaseline = timeBaseline + Dp.f(GAP_DP) + (-dm.ascent);

        float height = dateBaseline + dm.descent;
        float width = hoursWidth + sepWidth + minutesWidth;
        if (!suffix.isEmpty()) {
            width += Dp.f(6f) + suffixPaintWidth();
        }
        width = Math.max(width, datePaint.measureText(date));
        setMeasuredDimension((int) Math.ceil(width), (int) Math.ceil(height));
    }

    private float inkLeft(Paint paint, String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        paint.getTextBounds(text, 0, 1, inkBounds);
        return inkBounds.left;
    }

    private float suffixPaintWidth() {
        float was = timePaint.getTextSize();
        timePaint.setTextSize(Dp.sp(SUFFIX_SP));
        float w = timePaint.measureText(suffix);
        timePaint.setTextSize(was);
        return w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = -timeBearing;
        canvas.drawText(hours, x, timeBaseline, timePaint);
        x += hoursWidth;
        canvas.drawText(separator, x, timeBaseline, sepPaint);
        x += sepWidth;
        canvas.drawText(minutes, x, timeBaseline, timePaint);
        x += minutesWidth;

        if (!suffix.isEmpty()) {
            float was = timePaint.getTextSize();
            int wasColor = timePaint.getColor();
            timePaint.setTextSize(Dp.sp(SUFFIX_SP));
            timePaint.setColor(Palette.TEXT_TERTIARY);
            canvas.drawText(suffix, x + Dp.f(6f), timeBaseline, timePaint);
            timePaint.setTextSize(was);
            timePaint.setColor(wasColor);
        }

        if (!date.isEmpty()) {
            canvas.drawText(date, -dateBearing, dateBaseline, datePaint);
        }
    }
}
