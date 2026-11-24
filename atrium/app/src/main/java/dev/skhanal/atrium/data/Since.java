package dev.skhanal.atrium.data;

import android.content.Context;

import java.util.Calendar;

import dev.skhanal.atrium.R;

/**
 * How long ago something happened, in words.
 *
 * The hero says when you last opened the app you are pointing at. That is real
 * information this launcher actually has, because it recorded it itself when it
 * started the app. It is not watch history, and it does not pretend to be.
 *
 * The wording gets vaguer as it gets older on purpose. "47 hours ago" is a
 * number nobody wants. "Yesterday" is what a person would say.
 */
public final class Since {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private Since() {
    }

    public static String describe(Context c, long then, long now) {
        if (then <= 0) {
            return c.getString(R.string.never_opened);
        }
        long gap = now - then;
        if (gap < 0) {
            return c.getString(R.string.opened_just_now);
        }
        if (gap < 2 * MINUTE) {
            return c.getString(R.string.opened_just_now);
        }
        if (gap < HOUR) {
            int mins = (int) (gap / MINUTE);
            return c.getString(R.string.opened_minutes, mins);
        }
        if (sameDay(then, now)) {
            int hours = Math.max(1, (int) (gap / HOUR));
            return c.getResources().getQuantityString(R.plurals.opened_hours, hours, hours);
        }
        if (sameDay(then + 24 * HOUR, now)) {
            return c.getString(R.string.opened_yesterday);
        }
        int days = (int) (gap / (24 * HOUR));
        if (days < 7) {
            return c.getString(R.string.opened_days, Math.max(2, days));
        }
        return c.getString(R.string.opened_a_while);
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}
