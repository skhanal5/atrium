package dev.skhanal.atrium.system;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Settings;

/**
 * Everything Atrium hands over to the rest of the system.
 *
 * Fire OS decides what a normal app may start, and it is stricter than it first
 * looks. Three separate traps were found by testing on the device:
 *
 * 1. Most Fire TV settings screens are guarded by
 *    com.amazon.tv.permission.LAUNCHER_SETTINGS, which is signature|amazon. No
 *    installed app can hold it. Display and Sound, Accessibility, Preferences
 *    and My Account are all unreachable because of it.
 * 2. android.settings.SETTINGS has two handlers, one of which is a stub kept
 *    for compliance tests. Firing it lands the user on the Amazon home screen,
 *    which is the one place a replacement home screen must never send them.
 * 3. When an action has more than one handler, resolveActivity returns the
 *    system chooser rather than a real screen. An intent that "resolves" may
 *    still open nothing useful.
 *
 * So availability is decided by reading the target activity's own manifest
 * entry, not by asking whether an intent resolves. That is the only check that
 * knows about permissions.
 */
public final class Handoff {

    /**
     * Somewhere Atrium can send the user.
     *
     * The explicit component is tried first because implicit actions on this
     * firmware are unreliable. The action is kept as a fallback in case a
     * future build renames the activity.
     */
    public static final class Destination {
        public final int labelRes;
        private final ComponentName component;
        private final String action;

        public Destination(int labelRes, String pkg, String cls, String action) {
            this.labelRes = labelRes;
            this.component = pkg == null ? null : new ComponentName(pkg, cls);
            this.action = action;
        }

        /** True when this TV has the screen and will let us open it. */
        public boolean isAvailable(Context c) {
            return target(c) != null || (action != null && canStartAction(c, action));
        }

        private ComponentName target(Context c) {
            return canStart(c, component) ? component : null;
        }

        boolean open(Context c) {
            ComponentName cn = target(c);
            if (cn != null && start(c, new Intent(Intent.ACTION_MAIN).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                return true;
            }
            return action != null && start(c, new Intent(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private static final String SETTINGS_PKG = "com.amazon.tv.settings.v2";

    private Handoff() {
    }

    /**
     * The settings screens this TV actually lets a normal app open.
     *
     * Each one was started from Atrium on the device and checked. Anything that
     * was refused is simply not here, because a word in the footer that does
     * nothing is worse than no word at all.
     *
     * The applications screen is missing on purpose. It can be started, and it
     * then closes itself again within a few milliseconds when it is opened on
     * its own rather than from inside the settings hierarchy. A button that
     * flashes a screen and returns is worse than no button, so it was dropped.
     * The same screen does stay open when it is given a package to show, which
     * is what the App info item in the app menu does.
     */
    public static Destination[] destinations() {
        return new Destination[]{
                new Destination(dev.skhanal.atrium.R.string.footer_network,
                        SETTINGS_PKG, SETTINGS_PKG + ".tv.network.NetworkActivity",
                        Settings.ACTION_WIFI_SETTINGS),
                new Destination(dev.skhanal.atrium.R.string.footer_system,
                        SETTINGS_PKG, SETTINGS_PKG + ".tv.device.DeviceActivity",
                        Settings.ACTION_DEVICE_INFO_SETTINGS),
        };
    }

    public static boolean open(Context c, Destination d) {
        return d != null && d.open(c);
    }

    /**
     * Whether this app is allowed to start a given activity.
     *
     * Reading the manifest entry is what catches the permission wall. An intent
     * can resolve perfectly and still be refused at the moment it is started.
     */
    private static boolean canStart(Context c, ComponentName cn) {
        if (cn == null) {
            return false;
        }
        try {
            ActivityInfo ai = c.getPackageManager().getActivityInfo(cn, 0);
            if (!ai.exported || !ai.isEnabled()) {
                return false;
            }
            return ai.permission == null
                    || c.checkSelfPermission(ai.permission) == PackageManager.PERMISSION_GRANTED;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The same check for an implicit action.
     *
     * A resolution that lands on the system chooser is treated as unavailable:
     * it means several apps claim the action and none of them was chosen, so
     * the user would get a disambiguation dialog instead of a settings screen.
     */
    private static boolean canStartAction(Context c, String action) {
        try {
            ComponentName cn = new Intent(action).resolveActivity(c.getPackageManager());
            if (cn == null || cn.getClassName().contains("ResolverActivity")) {
                return false;
            }
            return canStart(c, cn);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Start an app.
     *
     * @return false when it could not be started, so the caller can tell the
     *         user instead of leaving the press looking ignored.
     */
    public static boolean launch(Context context, ComponentName component) {
        if (component == null) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (start(context, intent)) {
            return true;
        }
        // Some Fire OS apps refuse an explicit component but accept the
        // package's own declared entry point.
        try {
            Intent fallback = context.getPackageManager()
                    .getLaunchIntentForPackage(component.getPackageName());
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return start(context, fallback);
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return false;
    }

    /**
     * The system's details page for one app.
     *
     * The AOSP details action is handled here by the Fire TV applications
     * screen, which is reachable, so this one works.
     */
    public static boolean openAppInfo(Context context, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (start(context, i)) {
            return true;
        }
        ComponentName apps = new ComponentName(SETTINGS_PKG,
                SETTINGS_PKG + ".tv.applications.ApplicationsActivity");
        return canStart(context, apps) && start(context,
                new Intent(Intent.ACTION_MAIN).setComponent(apps)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /**
     * Open a link, in one named app when a package is given.
     *
     * Naming the app matters for YouTube links: several apps on this TV claim
     * them, and without a name Android shows a chooser instead.
     */
    public static boolean openUri(Context context, String uri, String pkg) {
        if (uri == null) {
            return false;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (pkg != null) {
            i.setPackage(pkg);
        }
        return start(context, i);
    }

    /** Open a package by its own declared entry point. */
    public static boolean launchPackage(Context context, String pkg) {
        try {
            Intent i = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                i = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setPackage(pkg);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return start(context, i);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean start(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException
                 | IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    /**
     * Whether the TV has a working connection.
     *
     * Used for one thing only: a quiet line appears when the TV is offline,
     * because most of these apps are useless without it. When the TV is online
     * nothing is shown, since that is the normal state and needs no comment.
     */
    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (RuntimeException e) {
            // Without the permission we cannot know, so we assume online and
            // stay quiet rather than showing a warning that might be wrong.
            return true;
        }
    }
}
