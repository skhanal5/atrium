package dev.skhanal.atrium.content;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.skhanal.atrium.BuildConfig;

/** Builds the content parts for this TV. The only class here that needs Android. */
public final class Content {

    /** Four detail requests at a time. More gains little on this TV's Wi-Fi. */
    private static final int DETAIL_THREADS = 4;

    private static ContentRepository repository;

    private Content() {
    }

    /** One shared repository, so two screens never fetch at the same time. */
    public static synchronized ContentRepository repository(Context context) {
        if (repository == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            repository = new ContentRepository(
                    new FeedStore(new File(app.getFilesDir(), "content")),
                    new FeedFetcher(new TmdbHttp(TmdbHttp.BASE, BuildConfig.TMDB_READ_TOKEN),
                            lowPriorityPool(DETAIL_THREADS, "atrium-tmdb")),
                    lowPriorityPool(1, "atrium-feed"),
                    main::post,
                    System::currentTimeMillis);
        }
        return repository;
    }

    /**
     * Package names of every app on the TV that can run, for matching
     * streaming services and marking apps Installed.
     *
     * Disabled apps are left out. The debloat disables some, such as Amazon
     * Music, and Android still lists them as installed. Counting them showed
     * Installed on an app that could not open. Found on the TV.
     */
    public static Set<String> installedPackages(Context context) {
        Set<String> out = new HashSet<>();
        try {
            for (PackageInfo p : context.getPackageManager().getInstalledPackages(0)) {
                if (p.applicationInfo != null && p.applicationInfo.enabled) {
                    out.add(p.packageName);
                }
            }
        } catch (RuntimeException e) {
            // The package list can fail when it is too large to send between
            // processes. Rows still load, just without "On your apps".
        }
        return out;
    }

    /**
     * Background threads below normal priority, so network work never takes
     * time from drawing. They are daemons, so they never keep the app alive.
     */
    private static ExecutorService lowPriorityPool(int threads, String name) {
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, name);
            t.setDaemon(true);
            return t;
        });
    }
}
