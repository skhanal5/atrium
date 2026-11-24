package dev.skhanal.atrium.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.skhanal.atrium.AtriumActivity;
import dev.skhanal.atrium.data.Prefs;

/**
 * Restarts the watchers after reboot or update. Opens Atrium after a reboot.
 *
 * Opening Atrium after reboot creates a task that HomeKeyService can move quickly.
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            WakeService.ensureRunning(context);
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) && new Prefs(context).replaceHome()) {
                try {
                    context.startActivity(new Intent(context, AtriumActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                } catch (RuntimeException ignored) {
                    // HomeKeyService can still open Atrium on a later press.
                }
            }
        }
    }
}
