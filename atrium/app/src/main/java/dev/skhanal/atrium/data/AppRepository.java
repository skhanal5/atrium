package dev.skhanal.atrium.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.skhanal.atrium.ui.Type;

/**
 * Finds the apps and gets them ready to draw.
 *
 * Two things make this more than a package query:
 *
 * 1. Fire OS apps are inconsistent about which launcher category they declare.
 *    Prime Video and the Appstore do not declare LEANBACK_LAUNCHER, so querying
 *    only that category misses them. Both categories are queried and merged.
 * 2. The slow work, which is loading and measuring every icon, happens off the
 *    main thread so the first frame is never held up by it.
 */
public final class AppRepository {

    /**
     * Packages that declare a launcher icon but are background services or the
     * home screen we are replacing. These are never offered, not even in the
     * hidden apps list, because showing them would only confuse.
     */
    private static final Set<String> NEVER_SHOW = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher",        // the stock Fire TV home
            "com.amazon.avod",               // playback activity closes when opened on its own
            "com.amazon.hedwig",             // Amazon background service
            "com.amazon.ssm",                // system service manager
            "com.amazon.vizzini.ftvcds"      // Alexa content discovery service
    ));

    /**
     * Hidden on first run only. These are real apps, just not ones worth a slot
     * on a home screen by default. The user can bring any of them back.
     */
    private static final List<String> HIDE_ON_FIRST_RUN = Arrays.asList(
            "com.amazon.tv.localgallery",
            "com.android.documentsui",
            "com.android.providers.downloads.ui"
    );

    public interface Listener {
        /** Called on the main thread once icons are loaded. */
        void onAppsReady(List<AppEntry> apps);
    }

    private final Context context;
    private final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;

    public AppRepository(Context context, Prefs prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    /** Query, order and load icons. The listener is called on the main thread. */
    public void load(final int iconSizePx, final Listener listener) {
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
        }
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> apps = loadBlocking(iconSizePx);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onAppsReady(apps);
                    }
                });
            }
        }, "atrium-apps");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private List<AppEntry> loadBlocking(int iconSizePx) {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> found = new LinkedHashMap<>();

        // Leanback first, so a TV-specific activity wins over a phone one when a
        // package declares both.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, found);
        collect(pm, Intent.CATEGORY_LAUNCHER, found);
        found.remove(context.getPackageName());

        if (!prefs.seeded()) {
            Set<String> seed = prefs.hidden();
            for (String p : HIDE_ON_FIRST_RUN) {
                if (found.containsKey(p)) {
                    seed.add(p);
                }
            }
            prefs.setHidden(seed);
            prefs.setSeeded();
        }

        for (String hidden : prefs.hidden()) {
            found.remove(hidden);
        }

        List<AppEntry> ordered = applyOrder(found);

        for (AppEntry e : ordered) {
            if (Thread.currentThread().isInterrupted()) {
                return ordered;
            }
            try {
                Drawable icon = pm.getActivityIcon(e.component);
                IconAnalysis a = AccentExtractor.analyse(icon);
                e.accent = a.accent;
                e.needsPlate = a.needsPlate;
                e.icon = AccentExtractor.compose(icon, iconSizePx, a.needsPlate);
            } catch (PackageManager.NameNotFoundException | RuntimeException ex) {
                // The app went away between the query and now. It simply draws
                // without an icon rather than taking the launcher down.
                e.icon = null;
            }
            e.typeface = Type.forLabel(e.label, Type.regular());
            e.lastOpened = prefs.lastOpened(e.pkg);
        }
        return ordered;
    }

    private void collect(PackageManager pm, String category, Map<String, AppEntry> into) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(intent, 0);
        } catch (RuntimeException e) {
            return;
        }
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            if (NEVER_SHOW.contains(pkg) || into.containsKey(pkg)) {
                continue;
            }
            CharSequence label = ri.loadLabel(pm);
            String name = label == null ? pkg : label.toString().trim();
            if (name.isEmpty()) {
                name = pkg;
            }
            into.put(pkg, new AppEntry(pkg,
                    new ComponentName(pkg, ri.activityInfo.name), name));
        }
    }

    /**
     * Put the apps in the user's saved order. Anything saved keeps its place,
     * anything new goes on the end in alphabetical order, so installing an app
     * never reshuffles the row.
     */
    private List<AppEntry> applyOrder(Map<String, AppEntry> found) {
        List<AppEntry> out = new ArrayList<>();
        for (String pkg : prefs.order()) {
            AppEntry e = found.remove(pkg);
            if (e != null) {
                out.add(e);
            }
        }
        List<AppEntry> rest = new ArrayList<>(found.values());
        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(rest, (a, b) -> collator.compare(a.label, b.label));
        out.addAll(rest);
        return out;
    }

    /** Put one app at the front of the row and keep everything else in order. */
    public void pinToFront(List<AppEntry> apps, AppEntry entry) {
        List<String> pkgs = new ArrayList<>(apps.size());
        pkgs.add(entry.pkg);
        for (AppEntry e : apps) {
            if (!e.pkg.equals(entry.pkg)) {
                pkgs.add(e.pkg);
            }
        }
        prefs.setOrder(pkgs);
    }

    /** Remember the order after the user moves an app. */
    public void saveOrder(List<AppEntry> apps) {
        List<String> pkgs = new ArrayList<>(apps.size());
        for (AppEntry e : apps) {
            pkgs.add(e.pkg);
        }
        prefs.setOrder(pkgs);
    }

    public void hide(String pkg) {
        Set<String> h = prefs.hidden();
        h.add(pkg);
        prefs.setHidden(h);
    }

    /** The hidden apps, with their names, for the preferences screen. */
    public List<AppEntry> hiddenApps() {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> found = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, found);
        collect(pm, Intent.CATEGORY_LAUNCHER, found);
        List<AppEntry> out = new ArrayList<>();
        for (String pkg : prefs.hidden()) {
            AppEntry e = found.get(pkg);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    public void unhide(String pkg) {
        Set<String> h = prefs.hidden();
        h.remove(pkg);
        prefs.setHidden(h);
    }

    public void unhideAll() {
        prefs.setHidden(new HashSet<String>());
    }
}
