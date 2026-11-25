package dev.skhanal.atrium;

import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import dev.skhanal.atrium.content.Genre;
import dev.skhanal.atrium.data.AppEntry;
import dev.skhanal.atrium.data.AppRepository;
import dev.skhanal.atrium.data.Prefs;
import dev.skhanal.atrium.system.Handoff;
import dev.skhanal.atrium.system.HomeWatcher;
import dev.skhanal.atrium.system.WakeService;
import dev.skhanal.atrium.ui.Motion;
import dev.skhanal.atrium.ui.Sheet;
import dev.skhanal.atrium.ui.SheetRow;

/**
 * The Settings menu, and the menus it leads to. They are drawn in the
 * activity's one {@link Sheet}.
 *
 * It is kept out of the activity so a new setting does not grow it. It reaches
 * the activity only through {@link Host}.
 */
final class SettingsMenu {

    /** What the menu needs from the activity. */
    interface Host {
        /** How many apps the home screen shows. */
        int appCount();

        /** Start moving apps in the Your apps row. */
        void rearrangeApps();

        /** Apps were shown again, so the Your apps row must reload. */
        void appsChanged();

        void clockChanged(boolean use24);

        /** Other genres were chosen, so the home screen fetches Your genres again. */
        void genresChanged();
    }

    /** One line in the settings menu, with the value it shows on the right. */
    private static final class Setting {
        final String label;
        final boolean closes;
        /** The current value, or null for a line with none. */
        final Supplier<String> value;
        /** Null for a line that is only text. */
        final Runnable action;

        Setting(String label, boolean closes, Supplier<String> value, Runnable action) {
            this.label = label;
            this.closes = closes;
            this.value = value;
            this.action = action;
        }
    }

    private final Context context;
    private final Sheet sheet;
    private final Prefs prefs;
    private final AppRepository repo;
    private final Handler handler;
    private final Host host;

    SettingsMenu(Context context, Sheet sheet, Prefs prefs, AppRepository repo, Handler handler, Host host) {
        this.context = context;
        this.sheet = sheet;
        this.prefs = prefs;
        this.repo = repo;
        this.handler = handler;
        this.host = host;
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    void open() {
        List<Setting> items = new ArrayList<>();
        items.add(new Setting("Rearrange apps", true, () -> String.valueOf(host.appCount()),
                host::rearrangeApps));
        items.add(new Setting("Hidden apps", true, () -> {
            int n = prefs.hidden().size();
            return n == 0 ? "None" : String.valueOf(n);
        }, () -> handler.post(this::openHiddenApps)));
        items.add(new Setting("Genres", true, () -> {
            int n = prefs.genres().size();
            return n == 0 ? "None" : String.valueOf(n);
        }, () -> handler.post(this::openGenres)));
        for (Handoff.Destination d : Handoff.destinations()) {
            if (d.isAvailable(context)) {
                items.add(new Setting(context.getString(d.labelRes), true, null, () -> Handoff.open(context, d)));
            }
        }
        items.add(new Setting("Reduce motion", false, () -> onOff(prefs.reduceMotion()), () -> {
            prefs.setReduceMotion(!prefs.reduceMotion());
            Motion.setReduced(prefs.reduceMotion());
        }));
        items.add(new Setting("24-hour clock", false, () -> onOff(prefs.clock24()), () -> {
            prefs.setClock24(!prefs.clock24());
            host.clockChanged(prefs.clock24());
        }));
        items.add(new Setting("Open Atrium on Home and wake", false, () -> onOff(prefs.replaceHome()), () -> {
            boolean on = !prefs.replaceHome();
            prefs.setReplaceHome(on);
            if (on) {
                WakeService.ensureRunning(context);
            } else {
                WakeService.stop(context);
            }
        }));
        if (prefs.replaceHome()) {
            // With the watcher on, the Amazon home screen is hard to reach, and
            // it is the only way into the settings Atrium cannot open.
            items.add(new Setting("Fire TV home screen", true, null, () -> {
                HomeWatcher.standDown(12000L);
                Handoff.launchPackage(context, "com.amazon.tv.launcher");
            }));
        }
        // The credit TMDB asks for. It is a line, not a button.
        items.add(new Setting("Movie data and artwork from TMDB", false, null, null));

        List<String> labels = new ArrayList<>();
        for (Setting s : items) {
            labels.add(s.label);
        }
        sheet.configure("Settings", labels, new Sheet.Callback() {
            @Override
            public void onSheetChoice(int index) {
                Setting s = items.get(index);
                if (s.action == null) {
                    return;
                }
                if (s.closes) {
                    sheet.close();
                }
                s.action.run();
                showValues(items);
            }

            @Override
            public void onSheetClosed() {
            }
        });
        showValues(items);
        sheet.rowAt(items.size() - 1).setRowEnabled(false);
        sheet.open(0);
    }

    /** Each setting shows its current value on the right, so nothing is hidden. */
    private void showValues(List<Setting> items) {
        for (int i = 0; i < items.size(); i++) {
            SheetRow row = sheet.rowAt(i);
            Supplier<String> value = items.get(i).value;
            if (row != null && value != null) {
                row.setValue(value.get());
            }
        }
    }

    /**
     * Pick genres for the Your genres row. Select turns one on or off and the
     * menu stays open, so several can be picked in one visit.
     */
    private void openGenres() {
        Set<Genre> before = prefs.genres();
        Set<Genre> chosen = EnumSet.noneOf(Genre.class);
        chosen.addAll(before);
        Genre[] all = Genre.values();
        List<String> options = new ArrayList<>();
        for (Genre g : all) {
            options.add(g.label);
        }
        options.add("Done");
        sheet.configure("Genres", options, new Sheet.Callback() {
            @Override
            public void onSheetChoice(int index) {
                if (index == all.length) {
                    sheet.close();
                    return;
                }
                Genre g = all[index];
                if (!chosen.remove(g)) {
                    chosen.add(g);
                }
                prefs.setGenres(chosen);
                sheet.rowAt(index).setValue(chosen.contains(g) ? "On" : null);
            }

            @Override
            public void onSheetClosed() {
                // Back closes the menu too, so the reload waits for the close.
                if (!chosen.equals(before)) {
                    host.genresChanged();
                }
            }
        });
        for (int i = 0; i < all.length; i++) {
            sheet.rowAt(i).setValue(chosen.contains(all[i]) ? "On" : null);
        }
        sheet.open(0);
    }

    private void openHiddenApps() {
        List<AppEntry> hidden = repo.hiddenApps();
        List<String> options = new ArrayList<>();
        for (AppEntry e : hidden) {
            options.add(e.label);
        }
        options.add(hidden.isEmpty() ? "Nothing is hidden" : "Show all apps again");
        sheet.configure("Hidden apps", options, new Sheet.Callback() {
            @Override
            public void onSheetChoice(int index) {
                sheet.close();
                if (hidden.isEmpty()) {
                    return;
                }
                if (index < hidden.size()) {
                    repo.unhide(hidden.get(index).pkg);
                } else {
                    repo.unhideAll();
                }
                host.appsChanged();
            }

            @Override
            public void onSheetClosed() {
            }
        });
        // Two system apps on this TV are both called "Downloads", so the
        // package name goes on the right to tell them apart.
        for (int i = 0; i < hidden.size(); i++) {
            sheet.rowAt(i).setValue(hidden.get(i).pkg);
        }
        sheet.open(0);
    }
}
