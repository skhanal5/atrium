package dev.skhanal.atrium.system;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.skhanal.atrium.AtriumActivity;
import dev.skhanal.atrium.data.Prefs;

/** Watches Home presses because Fire OS blocks the normal launcher choice. */
public final class HomeKeyService extends Service {
    private volatile boolean running;
    private volatile Process logcat;
    private Thread reader;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (reader == null) {
            running = true;
            reader = new Thread(this::follow, "atrium-home");
            reader.start();
        }
        return START_STICKY;
    }

    private void follow() {
        Prefs prefs = new Prefs(this);
        HomePress press = new HomePress();
        while (running) {
            try {
                // Start at the current time. Old Home presses must not interrupt playback.
                String since = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                Process process = new ProcessBuilder("logcat", "-b", "system", "-T", since,
                        "-v", "brief", "ContextImpl:W", "*:S").redirectErrorStream(true).start();
                logcat = process;
                if (!running) process.destroy();
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while (running && (line = lines.readLine()) != null) {
                        if (prefs.replaceHome() && press.accept(line, SystemClock.elapsedRealtime())) {
                            // The log arrives before Amazon opens Home. Let that start finish first.
                            SystemClock.sleep(500);
                            if (!running || !prefs.replaceHome()) continue;
                            // A deliberate Home press also ends the Fire TV settings handoff.
                            HomeWatcher.standDown(0);
                            if (!moveOwnTaskForward()) {
                                startActivity(new Intent(this, AtriumActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                            }
                        }
                    }
                } finally {
                    process.destroy();
                }
            } catch (Exception e) {
                if (running) Log.w("AtriumHome", "Home watcher stopped", e);
            }
            if (running) SystemClock.sleep(5000);
        }
    }

    private boolean moveOwnTaskForward() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
            try {
                task.moveToFront();
                // Once Atrium is in front, this intent resets its Home view.
                startActivity(new Intent(this, AtriumActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                return true;
            } catch (RuntimeException error) {
                Log.w("AtriumHome", "Could not open Atrium task", error);
            }
        }
        return false;
    }

    @Override
    public void onDestroy() {
        running = false;
        Process process = logcat;
        if (process != null) process.destroy();
        if (reader != null) reader.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
