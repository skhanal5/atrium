package dev.skhanal.atrium.system;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import dev.skhanal.atrium.AtriumActivity;
import dev.skhanal.atrium.data.Prefs;

/**
 * Opens Atrium when the TV is switched on.
 *
 * HomeKeyService handles Home presses. This service handles screen wake.
 *
 * Switching the TV on is not a boot. The TV sleeps rather than shuts down, so
 * ACTION_SCREEN_ON is the signal that matters, and it cannot be declared in the
 * manifest. That is the only reason this service exists: something has to be
 * running to hear it.
 *
 * It holds no wake lock, does no work while idle, and starts nothing unless the
 * user has turned the setting on.
 */
public final class WakeService extends Service {

    /**
     * How long to wait after the screen comes on.
     *
     * The Amazon launcher is coming up at the same moment. Starting Atrium
     * immediately means racing it and sometimes losing, which would leave the
     * user on the Amazon screen with no sign anything was meant to happen.
     * Letting it settle first and then opening over it is slower but certain.
     */
    private static final long SETTLE_MS = 900L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private boolean registered;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                return;
            }
            if (prefs == null || !prefs.replaceHome()) {
                return;
            }
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(WakeService.this::bringUpAtrium, SETTLE_MS);
        }
    };

    /** Start the service if the user has asked for this behaviour. */
    public static void ensureRunning(Context context) {
        try {
            Prefs p = new Prefs(context);
            if (p.replaceHome()) {
                context.startService(new Intent(context, WakeService.class));
                context.startService(new Intent(context, HomeKeyService.class));
            }
        } catch (RuntimeException ignored) {
            // Never let this stop the launcher from opening.
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, WakeService.class));
            context.stopService(new Intent(context, HomeKeyService.class));
        } catch (RuntimeException ignored) {
            // Nothing to do.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        if (!registered) {
            registerReceiver(screen, new IntentFilter(Intent.ACTION_SCREEN_ON));
            registered = true;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Restart if the system reclaims us, so turning the TV on still works
        // days later without anyone opening Atrium again.
        return START_STICKY;
    }

    private void bringUpAtrium() {
        if (prefs == null || !prefs.replaceHome()) {
            return;
        }
        if (SystemClock.elapsedRealtime() < HomeWatcher.standDownUntilMs()) {
            return;
        }
        try {
            startActivity(new Intent(this, AtriumActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (RuntimeException ignored) {
            // Leaving the Amazon screen up is the safe outcome.
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (registered) {
            try {
                unregisterReceiver(screen);
            } catch (IllegalArgumentException ignored) {
                // Already gone.
            }
            registered = false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;    // nothing binds to this
    }
}
