package dev.skhanal.atrium.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the user has chosen, kept on the device.
 *
 * Nothing here leaves the TV. There is no account and no cloud copy, so the
 * launcher works the same with the network unplugged.
 */
public final class Prefs {

    private static final String FILE = "atrium";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_LIGHT = "light";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";
    private static final String KEY_CLOCK_24 = "clock_24";
    private static final String KEY_LAST_FOCUS = "last_focus";
    private static final String KEY_SEEDED = "seeded";
    private static final String KEY_REPLACE_HOME = "replace_home";
    private static final String KEY_OPENED = "opened_";
    private static final String SEP = "\n";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- Order ----------------------------------------------------------

    public List<String> order() {
        return split(sp.getString(KEY_ORDER, ""));
    }

    public void setOrder(List<String> packages) {
        sp.edit().putString(KEY_ORDER, join(packages)).apply();
    }

    // ---- Hidden ---------------------------------------------------------

    public Set<String> hidden() {
        return new LinkedHashSet<>(split(sp.getString(KEY_HIDDEN, "")));
    }

    public void setHidden(Set<String> packages) {
        sp.edit().putString(KEY_HIDDEN, join(new ArrayList<>(packages))).apply();
    }

    // ---- Appearance -----------------------------------------------------

    public int light() {
        return sp.getInt(KEY_LIGHT, 0);
    }

    public void setLight(int preset) {
        sp.edit().putInt(KEY_LIGHT, preset).apply();
    }

    public boolean accentFromApp() {
        return sp.getBoolean(KEY_ACCENT, true);
    }

    public void setAccentFromApp(boolean on) {
        sp.edit().putBoolean(KEY_ACCENT, on).apply();
    }

    public boolean reduceMotion() {
        return sp.getBoolean(KEY_REDUCE_MOTION, false);
    }

    public void setReduceMotion(boolean on) {
        sp.edit().putBoolean(KEY_REDUCE_MOTION, on).apply();
    }

    public boolean clock24() {
        return sp.getBoolean(KEY_CLOCK_24, false);
    }

    public void setClock24(boolean on) {
        sp.edit().putBoolean(KEY_CLOCK_24, on).apply();
    }

    /**
     * Whether Home and screen wake should open Atrium.
     *
     * Off by default. The Home log watcher needs READ_LOGS granted over ADB.
     */
    public boolean replaceHome() {
        return sp.getBoolean(KEY_REPLACE_HOME, false);
    }

    public void setReplaceHome(boolean on) {
        sp.edit().putBoolean(KEY_REPLACE_HOME, on).apply();
    }

    // ---- First run ------------------------------------------------------

    /**
     * False until the first run has hidden the handful of bare system apps that
     * declare a launcher icon but are not things anyone opens from a home
     * screen. They go in the normal hidden set, so the user can bring them back.
     */
    public boolean seeded() {
        return sp.getBoolean(KEY_SEEDED, false);
    }

    public void setSeeded() {
        sp.edit().putBoolean(KEY_SEEDED, true).apply();
    }

    // ---- Focus restoration ----------------------------------------------

    public String lastFocus() {
        return sp.getString(KEY_LAST_FOCUS, null);
    }

    public void setLastFocus(String pkg) {
        sp.edit().putString(KEY_LAST_FOCUS, pkg).apply();
    }

    // ---- What was opened, and when --------------------------------------
    // The launcher records this itself, when it starts an app. It is the only
    // honest source of "recent" a third-party launcher on this TV has. Nothing
    // here is watch history, and none of it leaves the device.

    public long lastOpened(String pkg) {
        return sp.getLong(KEY_OPENED + pkg, 0L);
    }

    public void noteOpened(String pkg, long whenMillis) {
        sp.edit().putLong(KEY_OPENED + pkg, whenMillis).apply();
    }

    // ---- Helpers --------------------------------------------------------
    // A newline-joined string is used instead of a string set because a set has
    // no order, and the order of the apps is the point.

    private static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String s : raw.split(SEP)) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
