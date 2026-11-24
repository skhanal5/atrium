package dev.skhanal.atrium.system;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import dev.skhanal.atrium.AtriumActivity;
import dev.skhanal.atrium.data.Prefs;

/**
 * Legacy accessibility watcher for platforms that allow third-party services.
 * Fire OS 6 rejects this service. HomeKeyService uses the system log instead.
 */
public final class HomeWatcher extends AccessibilityService {

    /** Only this one screen is swapped. */
    private static final String AMAZON_PKG = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";

    /**
     * The Amazon launcher is still the only way into several settings screens
     * that are closed to us, so the user has to be able to get there. Atrium
     * sets this before it sends them, and the swap stands down until it passes.
     */
    private static volatile long standDownUntil = 0L;

    /** Guards against reacting twice to one screen change. */
    private long lastSwap = 0L;

    private Prefs prefs;

    /** Let the user reach the Amazon home screen without being bounced back. */
    public static void standDown(long millis) {
        standDownUntil = SystemClock.elapsedRealtime() + millis;
    }

    /** Shared with {@link WakeService}, which honours the same stand-down. */
    static long standDownUntilMs() {
        return standDownUntil;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        if (prefs == null) {
            prefs = new Prefs(this);
        }
        if (!prefs.replaceHome()) {
            return;
        }
        if (SystemClock.elapsedRealtime() < standDownUntil) {
            return;
        }

        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (!AMAZON_PKG.contentEquals(pkg == null ? "" : pkg)) {
            return;
        }
        // Match the home screen itself and nothing else. The same package also
        // owns the settings entry, and swallowing that would cut off the only
        // route to the settings Atrium cannot open.
        if (!AMAZON_HOME.contentEquals(cls == null ? "" : cls)) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastSwap < 1200L) {
            return;
        }
        lastSwap = now;

        try {
            startActivity(new Intent(this, AtriumActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (RuntimeException ignored) {
            // Nothing useful to do. Leaving the Amazon screen up is the safe
            // outcome, and the user can still open Atrium themselves.
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt. This service only watches.
    }

    /**
     * Whether the user has switched this service on in Fire TV settings.
     *
     * Atrium cannot turn it on for itself, and should not be able to. It is read
     * here only so the preferences screen can tell the user what state it is in
     * instead of offering a switch that silently does nothing.
     */
    public static boolean isEnabled(Context context) {
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(enabled)) {
                return false;
            }
            String me = new ComponentName(context, HomeWatcher.class).flattenToString();
            String meShort = new ComponentName(context, HomeWatcher.class).flattenToShortString();
            for (String part : enabled.split(":")) {
                if (part.equalsIgnoreCase(me) || part.equalsIgnoreCase(meShort)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
