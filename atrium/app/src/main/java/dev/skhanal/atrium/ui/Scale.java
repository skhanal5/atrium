package dev.skhanal.atrium.ui;

import android.view.View;

/**
 * The one focus movement every card and button shares: grow a little, fast.
 *
 * Only view properties change, so the GPU reuses what it already drew and a
 * focus change costs a matrix, not a redraw.
 */
final class Scale {

    private Scale() {
    }

    static void to(View v, float scale) {
        v.animate().cancel();
        long d = Motion.d(Motion.FOCUS);
        if (d == 0) {
            v.setScaleX(scale);
            v.setScaleY(scale);
            return;
        }
        v.animate().scaleX(scale).scaleY(scale).setDuration(d).setInterpolator(Motion.SWIFT).start();
    }
}
