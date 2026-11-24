package dev.skhanal.atrium;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.skhanal.atrium.content.Content;
import dev.skhanal.atrium.content.ContentRepository;
import dev.skhanal.atrium.content.Links;
import dev.skhanal.atrium.content.Title;
import dev.skhanal.atrium.content.TvApp;
import dev.skhanal.atrium.content.WatchOption;
import dev.skhanal.atrium.data.AppEntry;
import dev.skhanal.atrium.data.AppRepository;
import dev.skhanal.atrium.data.Prefs;
import dev.skhanal.atrium.system.Handoff;
import dev.skhanal.atrium.system.WakeService;
import dev.skhanal.atrium.ui.AppTile;
import dev.skhanal.atrium.ui.Backdrop;
import dev.skhanal.atrium.ui.Colors;
import dev.skhanal.atrium.ui.DetailScreen;
import dev.skhanal.atrium.ui.Dp;
import dev.skhanal.atrium.ui.FocusGrid;
import dev.skhanal.atrium.ui.Focusable;
import dev.skhanal.atrium.ui.GetAppsScreen;
import dev.skhanal.atrium.ui.HomeScreen;
import dev.skhanal.atrium.ui.SearchBox;
import dev.skhanal.atrium.ui.Motion;
import dev.skhanal.atrium.ui.Sheet;
import dev.skhanal.atrium.ui.TopBar;
import dev.skhanal.atrium.ui.Type;

/**
 * Atrium's home screen.
 *
 * Movies and shows from TMDB fill the screen, with your apps in a row under
 * them. Point at a title and its artwork fills the screen. Select it and the
 * detail screen says where you can watch it on this TV.
 *
 * Focus is moved by this activity with a {@link FocusGrid}, not by Android's
 * focus search. Two things use normal Android focus while they are open: the
 * settings menu, a {@link Sheet}, and the search field with the keyboard up.
 *
 * Row 0 of the home and Get apps screens is the top bar, which they share.
 */
public final class AtriumActivity extends Activity
        implements HomeScreen.Actions, DetailScreen.Actions, GetAppsScreen.Actions {

    /** Each screen, and the facts about it that the rest of the activity reads. */
    private enum Screen {
        HOME(0f, 0),
        DETAIL(0.4f, -1),
        APPS(0.75f, 1);

        /** How much darker the artwork is behind it, 0 to 1. */
        final float dim;
        /** Which top-bar item is this screen, or -1 when the top bar is hidden. */
        final int topItem;

        Screen(float dim, int topItem) {
            this.dim = dim;
            this.topItem = topItem;
        }

        boolean hasTopBar() {
            return topItem >= 0;
        }
    }

    /** The apps row, counting the top bar as row 0. */
    private static final int ROW_APPS = 1 + HomeScreen.ROW_APPS;
    /** The search field on Get apps, counting the top bar as row 0. */
    private static final int ROW_SEARCH = 1;

    private Prefs prefs;
    private AppRepository repo;
    private ContentRepository content;

    private Backdrop backdrop;
    private TopBar top;
    private HomeScreen home;
    private DetailScreen detail;
    private GetAppsScreen getApps;
    private Sheet sheet;
    private SettingsMenu settings;
    private TextView message;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();
    private List<AppTile> tiles = new ArrayList<>();
    private Set<String> installed = Collections.emptySet();

    private List<List<Focusable>> rows = new ArrayList<>();
    private Focusable current;
    private Screen screen = Screen.HOME;
    /** Where the remote's focus is on each screen. Each activity starts fresh. */
    private final Map<Screen, FocusGrid> grids = new EnumMap<>(Screen.class);

    {
        for (Screen s : Screen.values()) {
            grids.put(s, new FocusGrid());
        }
    }

    private boolean arranging;
    /** True once a held Select has opened the app menu, so its release opens nothing. */
    private boolean longPressed;

    // ---- Lifecycle ------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Dp.init(this);
        Type.init(this);
        prefs = new Prefs(this);
        Motion.setReduced(prefs.reduceMotion());
        repo = new AppRepository(this, prefs);
        content = Content.repository(this);
        installed = Content.installedPackages(this);

        WakeService.ensureRunning(this);
        buildViews();
        loadApps();
        loadContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(timeReceiver, timeFilter());
        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");
        registerReceiver(packageReceiver, pkg);

        top.tick();
        updateNotice();
        // A feed older than six hours is fetched again. A fresh one costs nothing.
        loadContent();
        if (current != null && screen == Screen.HOME) {
            // Restarts the hero's timer, which stopped when the app was left.
            applyFocus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregister(timeReceiver);
        unregister(packageReceiver);
        handler.removeCallbacks(reload);
        home.pause();
        if (arranging) {
            exitArrangeMode();
        }
    }

    private void unregister(BroadcastReceiver r) {
        try {
            unregisterReceiver(r);
        } catch (IllegalArgumentException ignored) {
            // Not registered. Nothing to undo.
        }
    }

    private static IntentFilter timeFilter() {
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        return f;
    }

    // ---- Views ----------------------------------------------------------

    private void buildViews() {
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        int match = ViewGroup.LayoutParams.MATCH_PARENT;

        backdrop = new Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(match, match));

        home = new HomeScreen(this, backdrop, this);
        root.addView(home, new FrameLayout.LayoutParams(match, match));

        detail = new DetailScreen(this, this);
        detail.setVisibility(View.GONE);
        root.addView(detail, new FrameLayout.LayoutParams(match, match));

        getApps = new GetAppsScreen(this, this);
        getApps.setVisibility(View.GONE);
        getApps.search.setListener(searchListener);
        root.addView(getApps, new FrameLayout.LayoutParams(match, match));

        top = new TopBar(this);
        top.addItem("Home", this::goHome);
        top.addItem("Get apps", this::openGetApps);
        top.addItem("Settings", () -> settings.open());
        top.items().get(Screen.HOME.topItem).setCurrent(true);
        top.setUse24(prefs.clock24());
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(match, Dp.of(36f));
        // The first item's own padding lines its text up with the margin.
        tp.leftMargin = Dp.of(HomeScreen.MARGIN_DP - 14f);
        tp.rightMargin = Dp.of(HomeScreen.MARGIN_DP);
        tp.topMargin = Dp.of(20f);
        root.addView(top, tp);

        message = new TextView(this);
        message.setTypeface(Type.semibold());
        message.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(12f));
        message.setTextColor(Colors.TEXT);
        message.setPadding(Dp.of(16f), Dp.of(10f), Dp.of(16f), Dp.of(10f));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF5141822);
        bg.setCornerRadius(Dp.f(9f));
        message.setBackground(bg);
        message.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mp.bottomMargin = Dp.of(30f);
        root.addView(message, mp);

        sheet = new Sheet(this);
        sheet.setVisibility(View.GONE);
        root.addView(sheet, new FrameLayout.LayoutParams(match, match));
        settings = new SettingsMenu(this, sheet, prefs, repo, handler, new SettingsMenu.Host() {
            @Override
            public int appCount() {
                return apps.size();
            }

            @Override
            public void rearrangeApps() {
                enterArrangeMode();
            }

            @Override
            public void appsChanged() {
                loadApps();
            }

            @Override
            public void clockChanged(boolean use24) {
                top.setUse24(use24);
            }
        });

        setContentView(root);
    }

    /** A short line at the bottom of the screen, for when something would not open. */
    private void say(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideMessage);
        handler.postDelayed(hideMessage, 3200);
    }

    private final Runnable hideMessage = () -> message.setVisibility(View.GONE);

    // ---- Loading --------------------------------------------------------

    private void loadApps() {
        if (arranging) {
            // Rebuilding the row mid-move would throw the move away. The
            // reload happens when the move ends.
            return;
        }
        repo.load(Dp.of(AppTile.ICON_DP), ready -> {
            String focusedPkg = focusedApp();
            boolean first = tiles.isEmpty() && current == null;
            apps.clear();
            apps.addAll(ready);
            tiles = home.setApps(apps);
            installed = Content.installedPackages(this);
            getApps.setInstalled(installed);
            // A streaming app may have come or gone, which changes "On your apps".
            // When it has not, this costs nothing.
            loadContent();
            current = null;
            refreshRows();
            if (first || focusedPkg != null) {
                focusApps(first ? prefs.lastFocus() : focusedPkg);
            } else {
                applyFocus();
            }
        });
    }

    private void loadContent() {
        content.load(installed, feed -> {
            if (home.setFeed(feed, installed)) {
                current = null;
                refreshRows();
                applyFocus();
            }
        });
    }

    // ---- Focus ----------------------------------------------------------

    private FocusGrid grid() {
        return grid(screen);
    }

    private FocusGrid grid(Screen s) {
        return grids.get(s);
    }

    /**
     * Focus that arrives in the top bar lands on this screen's item, not on
     * the item used last. That item can name another screen: the one chosen
     * when this screen was left.
     */
    private void landInTopBar() {
        if (screen.hasTopBar() && grid().row() == 0) {
            grid().focus(0, screen.topItem);
        }
    }

    /** Re-read the rows of the screen on show, keeping focus where it was. */
    private void refreshRows() {
        rows = new ArrayList<>();
        if (screen.hasTopBar()) {
            rows.add(new ArrayList<Focusable>(top.items()));
        }
        rows.addAll(screen == Screen.HOME ? home.focusRows()
                : screen == Screen.APPS ? getApps.focusRows() : detail.focusRows());
        int[] sizes = new int[rows.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = rows.get(i).size();
        }
        grid().setRows(sizes);
    }

    private Focusable focused() {
        FocusGrid g = grid();
        if (g.row() < 0 || g.row() >= rows.size() || g.column() >= rows.get(g.row()).size()) {
            return null;
        }
        return rows.get(g.row()).get(g.column());
    }

    /** Draw focus on whatever the grid points at, and tell the screen. */
    private void applyFocus() {
        Focusable f = focused();
        if (f != current) {
            if (current != null) {
                current.setFocused(false);
            }
            if (f != null) {
                f.setFocused(true);
            }
            current = f;
        }
        if (f != null && screen == Screen.HOME) {
            // The home screen counts its rows from below the top bar.
            home.onFocus(grid().row() - 1, grid().column(), f);
            if (f instanceof AppTile) {
                prefs.setLastFocus(((AppTile) f).entry().pkg);
            }
        }
    }

    /** Focus an app by package, or the first app. Coming back to the row you left matters. */
    private void focusApps(String pkg) {
        int index = 0;
        for (int i = 0; pkg != null && i < tiles.size(); i++) {
            if (tiles.get(i).entry().pkg.equals(pkg)) {
                index = i;
            }
        }
        grid(Screen.HOME).focus(ROW_APPS, index);
        applyFocus();
    }

    private String focusedApp() {
        return current instanceof AppTile ? ((AppTile) current).entry().pkg : null;
    }

    // ---- Home screen actions --------------------------------------------

    @Override
    public void openApp(AppEntry e) {
        if (arranging) {
            exitArrangeMode();
            return;
        }
        prefs.setLastFocus(e.pkg);
        if (Handoff.launch(this, e.component)) {
            // Recorded by this launcher when it starts the app. It is not watch history.
            prefs.noteOpened(e.pkg, System.currentTimeMillis());
            return;
        }
        say(e.label + " would not open");
    }

    /**
     * Switch screens. This is the one place that decides what is visible, how
     * dark the artwork is, and which focus grid the remote moves.
     */
    private void show(Screen next) {
        if (screen == Screen.HOME && next != Screen.HOME) {
            home.pause();
        }
        if (screen == Screen.APPS && getApps.search.isEditing()) {
            getApps.search.stopEditing();
        }
        screen = next;
        home.setVisibility(next == Screen.HOME ? View.VISIBLE : View.GONE);
        detail.setVisibility(next == Screen.DETAIL ? View.VISIBLE : View.GONE);
        getApps.setVisibility(next == Screen.APPS ? View.VISIBLE : View.GONE);
        top.setVisibility(next.hasTopBar() ? View.VISIBLE : View.GONE);
        if (next.hasTopBar()) {
            for (int i = 0; i < top.items().size(); i++) {
                top.items().get(i).setCurrent(i == next.topItem);
            }
        }
        backdrop.setDim(next.dim);
        if (next == Screen.HOME) {
            home.restoreBackdrop();
        }
        if (current != null) {
            current.setFocused(false);
        }
        current = null;
        refreshRows();
        landInTopBar();
    }

    /** Back to the home screen, where focus was. Pressed on Home, to the first app. */
    private void goHome() {
        if (screen == Screen.HOME) {
            focusApps(null);
            return;
        }
        show(Screen.HOME);
        applyFocus();
    }

    @Override
    public void openTitle(Title t) {
        detail.show(t, installed);
        show(Screen.DETAIL);
        backdrop.showArtwork(t.backdrop);
        grid(Screen.DETAIL).focus(0, 0);
        applyFocus();
    }

    @Override
    public void playTrailer(Title t) {
        String uri = t.details == null ? null : Links.trailer(t.details.trailer);
        if (uri == null) {
            say("TMDB has no trailer for this title");
            return;
        }
        if (!Handoff.openUri(this, uri, Links.YOUTUBE_PACKAGE)) {
            say("YouTube would not open");
        }
    }

    // ---- Detail screen actions ------------------------------------------

    @Override
    public void watch(WatchOption w) {
        String pkg = w.app.installedPackage(installed);
        if (pkg != null) {
            // TMDB does not give these apps' own title IDs, so the app opens
            // at its home screen rather than at the title.
            if (!Handoff.launchPackage(this, pkg)) {
                say(w.app.name + " would not open");
            }
            return;
        }
        if (!Handoff.openUri(this, Links.appstorePage(w.app.appstorePackage()), null)) {
            say("The Appstore would not open");
        }
    }

    @Override
    public void close() {
        if (screen != Screen.DETAIL) {
            return;
        }
        show(Screen.HOME);
        applyFocus();
    }

    // ---- Get apps -------------------------------------------------------

    @Override
    public void openGetApps() {
        if (screen == Screen.APPS) {
            return;
        }
        show(Screen.APPS);
        if (grid(Screen.APPS).row() < 0) {
            grid(Screen.APPS).focus(ROW_SEARCH, 0);
        }
        applyFocus();
    }

    @Override
    public void getApp(TvApp app) {
        String pkg = app.installedPackage(installed);
        boolean opened = pkg != null ? Handoff.launchPackage(this, pkg)
                : Handoff.openUri(this, Links.appstorePage(app.appstorePackage()), null);
        if (!opened) {
            say(pkg != null ? app.name + " would not open" : "The Appstore would not open");
        }
    }

    @Override
    public void openAppstore() {
        if (!Handoff.openAppstore(this)) {
            say("The Appstore would not open");
        }
    }

    private final SearchBox.Listener searchListener = new SearchBox.Listener() {
        @Override
        public void onSearch(String text) {
            getApps.show(text);
            current = null;
            refreshRows();
            grid(Screen.APPS).focus(ROW_SEARCH, 0);
            applyFocus();
        }

        @Override
        public void onDone() {
            getApps.search.stopEditing();
            // Straight to the first result, which is what a person looks at next.
            grid(Screen.APPS).focus(ROW_SEARCH + 1, 0);
            applyFocus();
        }
    };

    // ---- App menu and moving apps ---------------------------------------

    private void openAppMenu(AppTile tile) {
        AppEntry e = tile.entry();
        List<String> options = new ArrayList<>();
        options.add("Put first in the row");
        options.add("Move this app");
        options.add("Hide from home");
        options.add("App info");
        sheet.configure(e.label, options, new Sheet.Callback() {
            @Override
            public void onSheetChoice(int index) {
                sheet.close();
                switch (index) {
                    case 0:
                        repo.pinToFront(apps, e);
                        prefs.setLastFocus(e.pkg);
                        loadApps();
                        break;
                    case 1:
                        enterArrangeMode();
                        break;
                    case 2:
                        repo.hide(e.pkg);
                        loadApps();
                        break;
                    default:
                        Handoff.openAppInfo(AtriumActivity.this, e.pkg);
                        break;
                }
            }

            @Override
            public void onSheetClosed() {
            }
        });
        sheet.open(0);
    }

    private void enterArrangeMode() {
        if (!(current instanceof AppTile)) {
            focusApps(prefs.lastFocus());
        }
        if (!(current instanceof AppTile)) {
            return;
        }
        arranging = true;
        ((AppTile) current).setMoving(true);
        say("Left and right to move  ·  Select when done");
    }

    private void exitArrangeMode() {
        arranging = false;
        for (AppTile t : tiles) {
            t.setMoving(false);
        }
        repo.saveOrder(apps);
        handler.removeCallbacks(hideMessage);
        message.setVisibility(View.GONE);
    }

    /** Swap the app being moved with its neighbour, and keep focus on it. */
    private void moveApp(int delta) {
        int from = grid(Screen.HOME).column();
        int to = from + delta;
        if (grid(Screen.HOME).row() != ROW_APPS || to < 0 || to >= tiles.size()) {
            return;
        }
        Collections.swap(apps, from, to);
        Collections.swap(tiles, from, to);
        home.appsRow().swap(from, to);
        refreshRows();
        grid(Screen.HOME).focus(ROW_APPS, to);
        applyFocus();
    }

    // ---- Keys -----------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (sheet.isOpen()) {
            return super.dispatchKeyEvent(event);
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();

        // While the keyboard has the search field, keys belong to it. Back
        // gives the remote back to the grid.
        if (screen == Screen.APPS && getApps.search.isEditing()) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (!down) {
                    getApps.search.stopEditing();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // While an app is being moved, these keys are consumed whole, press
        // and release. Consuming only the press would let the release of the
        // Select that ends the move open the app underneath.
        if (arranging) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) {
                        moveApp(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (!down) {
                        exitArrangeMode();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                default:
                    break;
            }
        }

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    moveFocus(code);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return onSelect(event);
            case KeyEvent.KEYCODE_MENU:
                // On release. Opened on the press, the release would reach the
                // menu that just opened, and a Menu release closes a menu.
                if (!down && current instanceof AppTile) {
                    openAppMenu((AppTile) current);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Back goes to the home screen. On the home screen it has
                // nowhere to go, so it is swallowed rather than dropping the
                // user onto a black screen.
                if (!down && screen != Screen.HOME) {
                    goHome();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void moveFocus(int code) {
        FocusGrid g = grid();
        int fromRow = g.row();
        boolean moved = g.move(
                code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0,
                code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0);
        if (!moved) {
            return;
        }
        // Rows below the top bar keep their memory, which is what makes coming
        // back to a row land where you left it.
        if (fromRow != 0) {
            landInTopBar();
        }
        applyFocus();
    }

    /**
     * Select acts on release. Held on an app, it opens the app menu instead,
     * and its release then does nothing.
     */
    private boolean onSelect(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                longPressed = false;
            } else if (!longPressed && current instanceof AppTile && (event.isLongPress()
                    || event.getEventTime() - event.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                longPressed = true;
                openAppMenu((AppTile) current);
            }
            return true;
        }
        if (!longPressed && !event.isCanceled() && current != null) {
            current.activate();
        }
        longPressed = false;
        return true;
    }

    @Override
    public void onBackPressed() {
        // Deliberately empty. See dispatchKeyEvent.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already here settles the screen, it does not rebuild it.
        if (arranging) {
            exitArrangeMode();
        }
        if (sheet.isOpen()) {
            sheet.close();
        }
        String pkg = focusedApp() != null ? focusedApp() : prefs.lastFocus();
        if (screen != Screen.HOME) {
            show(Screen.HOME);
        }
        focusApps(pkg);
    }

    // ---- Receivers ------------------------------------------------------

    private void updateNotice() {
        top.setNotice(Handoff.isOnline(this) ? null : "Offline");
    }

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            top.tick();
            updateNotice();
        }
    };

    /**
     * Installing or updating one app fires several broadcasts in a row. They
     * are collapsed into one reload, so focus is not thrown away four times.
     */
    private final Runnable reload = this::loadApps;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(reload);
            handler.postDelayed(reload, 500);
        }
    };
}
