package dev.skhanal.atrium;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import dev.skhanal.atrium.data.AppEntry;
import dev.skhanal.atrium.data.AppRepository;
import dev.skhanal.atrium.data.Prefs;
import dev.skhanal.atrium.data.Since;
import dev.skhanal.atrium.system.Handoff;
import dev.skhanal.atrium.system.HomeWatcher;
import dev.skhanal.atrium.system.WakeService;
import dev.skhanal.atrium.ui.AppCard;
import dev.skhanal.atrium.ui.AppsSection;
import dev.skhanal.atrium.ui.ClockView;
import dev.skhanal.atrium.ui.Dp;
import dev.skhanal.atrium.ui.EmptyView;
import dev.skhanal.atrium.ui.Field;
import dev.skhanal.atrium.ui.FieldCache;
import dev.skhanal.atrium.ui.FieldView;
import dev.skhanal.atrium.ui.Glyph;
import dev.skhanal.atrium.ui.Hero;
import dev.skhanal.atrium.ui.HomeLayout;
import dev.skhanal.atrium.ui.Label;
import dev.skhanal.atrium.ui.Motion;
import dev.skhanal.atrium.ui.Palette;
import dev.skhanal.atrium.ui.Pill;
import dev.skhanal.atrium.ui.RowScroller;
import dev.skhanal.atrium.ui.RailItem;
import dev.skhanal.atrium.ui.Sheet;
import dev.skhanal.atrium.ui.ToolChip;
import dev.skhanal.atrium.ui.Type;

/**
 * Atrium's home screen.
 *
 * The screen is one room, lit by whatever the remote is pointing at. Move along
 * the row of apps and the whole screen changes colour, because the colour is
 * read out of that app's own icon and the artwork behind it is generated from
 * it. The hero names what you are pointing at. The rail says where you are. The
 * tools row holds the few things this TV will actually let a launcher open.
 *
 * Everything shown here is real. The clock is the clock, the apps are the apps
 * that are installed, "last opened" is a time this launcher recorded when it
 * started that app, and the artwork is made here rather than downloaded. There
 * is no watch history, because no third-party launcher on this TV can read one,
 * and no row of films nobody can play.
 */
public final class AtriumActivity extends Activity {

    private static final int SECTION_HOME = 0;
    private static final int SECTION_APPS = 1;

    private Prefs prefs;
    private AppRepository repo;
    private FieldCache fields;

    private FieldView field;
    private HomeLayout home;
    private ClockView clock;
    private LinearLayout rail;
    private Hero hero;
    private LinearLayout pills;
    private Pill openPill;
    private Pill optionsPill;
    private Label appsLabel;
    private Label toolsLabel;
    private RowScroller appsScroll;
    private LinearLayout appsRow;
    private LinearLayout toolsRow;
    private EmptyView emptyView;
    private AppsSection appsSection;
    private Sheet sheet;

    private RailItem railHome;
    private RailItem railApps;
    private RailItem railSettings;

    private final List<AppEntry> apps = new ArrayList<>();
    private final List<Tool> tools = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private AppEntry currentApp;
    private AppCard focusedCard;
    private View focusBeforeSheet;
    private int section = SECTION_HOME;
    private boolean arranging;
    private int lastHour = -1;
    private Thread warmer;

    /** One destination in the tools row. A null action means one of ours. */
    private static final class Tool {
        final String title;
        final String sub;
        final int glyph;
        final int accent;
        final Handoff.Destination destination;
        final int ours;     // 0 none, 1 Fire TV home, 2 Atrium preferences

        Tool(String title, String sub, int glyph, int accent,
             Handoff.Destination destination, int ours) {
            this.title = title;
            this.sub = sub;
            this.glyph = glyph;
            this.accent = accent;
            this.destination = destination;
            this.ours = ours;
        }
    }

    // ---- Lifecycle ------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Dp.init(this);
        Type.init(this);
        prefs = new Prefs(this);
        Motion.setReduced(prefs.reduceMotion());
        repo = new AppRepository(this, prefs);
        fields = new FieldCache();

        WakeService.ensureRunning(this);
        buildViews();
        field.setFallback(light().baseBottom);
        field.wake();
        loadApps();
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

        clock.update();
        updateNotice();
        refreshHero();
        // Coming back from an app must not replay the opening.
        field.setWoken();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregister(timeReceiver);
        unregister(packageReceiver);
        handler.removeCallbacks(reload);
        if (currentApp != null) {
            prefs.setLastFocus(currentApp.pkg);
        }
        if (arranging) {
            exitArrangeMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fields.stop();
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

    // ---- View tree ------------------------------------------------------

    private void buildViews() {
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);

        field = new FieldView(this);
        root.addView(field, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clock = new ClockView(this);
        clock.setUse24(prefs.clock24());

        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setClipChildren(false);
        railHome = addRailItem(getString(R.string.rail_home), Glyph.HOME);
        railApps = addRailItem(getString(R.string.rail_apps), Glyph.APPS);
        railSettings = addRailItem(getString(R.string.rail_settings), Glyph.SETTINGS);
        railHome.setCurrent(true);

        hero = new Hero(this);

        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setClipChildren(false);
        openPill = new Pill(this);
        openPill.set(getString(R.string.action_open), true);
        optionsPill = new Pill(this);
        optionsPill.set(getString(R.string.action_options), false);
        for (Pill p : new Pill[]{openPill, optionsPill}) {
            p.setId(View.generateViewId());
            p.setOnFocusChangeListener((v, has) -> ((Pill) v).applyFocus(has));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Dp.of(Pill.HEIGHT_DP));
            lp.setMarginEnd(Dp.of(14f));
            pills.addView(p, lp);
        }
        openPill.setNextFocusRightId(optionsPill.getId());
        optionsPill.setNextFocusLeftId(openPill.getId());
        optionsPill.setNextFocusRightId(optionsPill.getId());
        openPill.setOnClickListener(v -> openCurrentApp());
        optionsPill.setOnClickListener(v -> {
            if (focusedCard != null) {
                openAppMenu(focusedCard);
            }
        });

        appsLabel = new Label(this);
        appsLabel.setText(getString(R.string.row_apps));
        toolsLabel = new Label(this);
        toolsLabel.setText(getString(R.string.row_tools));

        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        // Each app draws its full border inside its card.
        appsScroll = new RowScroller(this);
        appsScroll.addView(appsRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setClipChildren(false);
        toolsRow.setPadding(0, Dp.of(HomeLayout.TOOLS_HEADROOM), 0, 0);
        // A focused chip grows a little out of the top of the row, into that
        // padding. Without this the top edge of it is cut off.
        toolsRow.setClipToPadding(false);

        emptyView = new EmptyView(this);
        emptyView.setText(getString(R.string.empty_title), getString(R.string.empty_body));
        emptyView.setVisibility(View.GONE);

        home = new HomeLayout(this);
        home.attach(clock, rail, hero, pills, appsLabel, appsScroll,
                toolsLabel, toolsRow, emptyView);
        root.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        appsSection = new AppsSection(this);
        appsSection.setHeader(getString(R.string.section_all_apps));
        appsSection.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sp.leftMargin = Dp.of(HomeLayout.CONTENT_X);
        sp.topMargin = Dp.of(96f);
        sp.rightMargin = Dp.of(HomeLayout.RIGHT_MARGIN);
        sp.bottomMargin = Dp.of(40f);
        root.addView(appsSection, sp);

        sheet = new Sheet(this);
        sheet.setVisibility(View.GONE);
        root.addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        buildTools();
        wireAppsSection();
    }

    private RailItem addRailItem(String label, int glyph) {
        RailItem item = new RailItem(this);
        item.set(label, glyph);
        item.setId(View.generateViewId());
        item.setOnFocusChangeListener((v, has) -> {
            ((RailItem) v).applyFocus(has);
        });
        item.setOnClickListener(this::onRailChosen);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Dp.of(RailItem.WIDTH_DP), Dp.of(RailItem.HEIGHT_DP));
        lp.bottomMargin = Dp.of(HomeLayout.RAIL_GAP);
        rail.addView(item, lp);
        return item;
    }

    private void wireAppsSection() {
        appsSection.setListener(new AppsSection.Listener() {
            @Override
            public void onAppFocused(AppCard card) {
                onCardFocused(card);
            }

            @Override
            public void onAppChosen(AppCard card) {
                onCardChosen(card);
            }

            @Override
            public void onAppMenu(AppCard card) {
                openAppMenu(card);
            }
        });
    }

    // ---- The tools row --------------------------------------------------

    /**
     * Only destinations this TV actually has.
     *
     * Each one is checked against the package manager first, so there is never
     * a chip here that does nothing when it is pressed. There is no Inputs chip
     * because there cannot be one: both the Fire TV inputs screen and the
     * tune-to-input intent are held at protection level signature|amazon, which
     * no installed app can hold. A dead chip would be a lie.
     */
    private void buildTools() {
        tools.clear();
        toolsRow.removeAllViews();

        for (Handoff.Destination d : Handoff.destinations()) {
            if (!d.isAvailable(this)) {
                continue;
            }
            boolean network = d.labelRes == R.string.footer_network;
            tools.add(new Tool(getString(d.labelRes),
                    getString(network ? R.string.tool_network_sub : R.string.tool_system_sub),
                    network ? Glyph.WIFI : Glyph.DISPLAY,
                    network ? 0xFF7FB2FF : 0xFFE3C39C, d, 0));
        }
        // When the watcher is running the Amazon home screen becomes hard to
        // reach, and it is still the only way into the settings Atrium cannot
        // open. This puts it one press away rather than trapping the user.
        if (prefs.replaceHome()) {
            tools.add(new Tool(getString(R.string.footer_firetv),
                    getString(R.string.tool_firetv_sub), Glyph.TV, 0xFFA98BFF, null, 1));
        }
        tools.add(new Tool(getString(R.string.footer_atrium),
                getString(R.string.tool_atrium_sub), Glyph.ATRIUM, light().key, null, 2));

        int gap = Dp.of(ToolChip.GAP_DP);
        int n = tools.size();
        int widthDp = Math.round((HomeLayout.contentWidthDp() - (n - 1) * ToolChip.GAP_DP) / n);

        for (int i = 0; i < n; i++) {
            final Tool t = tools.get(i);
            ToolChip chip = new ToolChip(this);
            chip.setWidthDp(widthDp);
            chip.set(t.title, t.sub, t.glyph, t.accent,
                    Field.tile(Field.specFor(t.accent, light(), t.title), 96, 36));
            chip.setId(View.generateViewId());
            chip.setOnFocusChangeListener((v, has) -> ((ToolChip) v).applyFocus(has));
            chip.setOnClickListener(v -> {
                ((ToolChip) v).playPress();
                onToolChosen(t);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Dp.of(widthDp), Dp.of(ToolChip.HEIGHT_DP));
            lp.setMarginEnd(i == n - 1 ? 0 : gap);
            toolsRow.addView(chip, lp);
        }
        lockRow(toolsRow, railHome.getId());
    }

    private void onToolChosen(Tool t) {
        if (t.ours == 2) {
            openPreferences();
            return;
        }
        if (t.ours == 1) {
            openAmazonHome();
            return;
        }
        if (!Handoff.open(this, t.destination)) {
            hero.show(getString(R.string.row_tools), t.title,
                    getString(R.string.unavailable), accentOfCurrent());
        }
    }

    /**
     * Send the user to the Amazon home screen on purpose.
     *
     * The watcher is told to stand down first, or it would bounce them straight
     * back here and the chip would look broken.
     */
    private void openAmazonHome() {
        HomeWatcher.standDown(12000L);
        Handoff.launchPackage(this, "com.amazon.tv.launcher");
    }

    // ---- The rail -------------------------------------------------------

    private void onRailChosen(View v) {
        if (v == railSettings) {
            openPreferences();
            return;
        }
        showSection(v == railApps ? SECTION_APPS : SECTION_HOME, true);
    }

    /**
     * Swap the middle of the screen.
     *
     * The rail, the clock and the room stay exactly where they are. Only the
     * content between them changes, which is what makes moving between Home and
     * Apps feel like one screen rather than two.
     */
    private void showSection(int next, boolean moveFocus) {
        if (section == next && !moveFocus) {
            return;
        }
        section = next;
        railHome.setCurrent(next == SECTION_HOME);
        railApps.setCurrent(next == SECTION_APPS);

        boolean appsOpen = next == SECTION_APPS;
        field.setDimmed(appsOpen);
        int homeVis = appsOpen ? View.GONE : View.VISIBLE;
        hero.setVisibility(homeVis);
        pills.setVisibility(homeVis);
        appsLabel.setVisibility(homeVis);
        appsScroll.setVisibility(apps.isEmpty() ? View.GONE : homeVis);
        toolsLabel.setVisibility(homeVis);
        toolsRow.setVisibility(homeVis);
        emptyView.setVisibility(!appsOpen && apps.isEmpty() ? View.VISIBLE : View.GONE);

        if (appsOpen) {
            appsSection.bind(apps);
            appsSection.setVisibility(View.VISIBLE);
            long d = Motion.d(Motion.SECTION);
            appsSection.setAlpha(d == 0 ? 1f : 0f);
            appsSection.setTranslationY(d == 0 ? 0f : Dp.f(14f));
            appsSection.animate().alpha(1f).translationY(0f)
                    .setDuration(d).setInterpolator(Motion.SWIFT).start();
            if (moveFocus) {
                View target = appsSection.cardFor(currentApp == null ? null : currentApp.pkg);
                if (target != null) {
                    target.requestFocus();
                }
            }
        } else {
            appsSection.setVisibility(View.GONE);
            if (moveFocus) {
                restoreFocus();
            }
        }
    }

    // ---- Apps -----------------------------------------------------------

    private void loadApps() {
        if (arranging) {
            // Rebuilding the row mid-move would throw the move away. The reload
            // happens when the move ends.
            return;
        }
        repo.load(Dp.of(AppCard.TILE_DP), ready -> {
            if (appsRow.getChildCount() > 0 && samePackages(ready)) {
                // Nothing we show has changed. Leaving the row alone keeps the
                // focus and the scroll position exactly where they were.
                return;
            }
            apps.clear();
            apps.addAll(ready);
            buildAppsRow();
            warmFields();
        });
    }

    private boolean samePackages(List<AppEntry> next) {
        if (next.size() != apps.size()) {
            return false;
        }
        for (int i = 0; i < next.size(); i++) {
            if (!next.get(i).pkg.equals(apps.get(i).pkg)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build the rooms for the apps in the background, before they are needed.
     *
     * The first one is the app that will be focused, so the screen is right by
     * the time anyone looks at it. The rest follow quietly, which is why moving
     * along the row is instant rather than catching up.
     */
    private void warmFields() {
        if (warmer != null && warmer.isAlive()) {
            warmer.interrupt();
        }
        final List<AppEntry> snapshot = new ArrayList<>(apps);
        final String first = prefs.lastFocus();
        warmer = new Thread(() -> {
            for (AppEntry e : snapshot) {
                if (e.pkg.equals(first)) {
                    fields.warm(e.pkg, specFor(e));
                }
            }
            for (AppEntry e : snapshot) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                fields.warm(e.pkg, specFor(e));
            }
        }, "atrium-warm");
        warmer.setPriority(Thread.MIN_PRIORITY);
        warmer.start();
    }

    private Field.Spec specFor(AppEntry e) {
        int accent = prefs.accentFromApp() ? e.accent : 0;
        return Field.specFor(accent, light(), e.pkg);
    }

    private void buildAppsRow() {
        appsRow.removeAllViews();
        focusedCard = null;

        if (apps.isEmpty()) {
            // There is nothing for the grid to show, so the user is put back on
            // the home screen rather than left looking at an empty section.
            if (section == SECTION_APPS) {
                showSection(SECTION_HOME, false);
            }
            appsScroll.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            hero.show(timeWord(), getString(R.string.empty_title),
                    getString(R.string.hero_empty_body), 0);
            pills.setVisibility(View.GONE);
            // Focus has to land somewhere, or the remote does nothing at all.
            if (toolsRow.getChildCount() > 0) {
                toolsRow.getChildAt(0).requestFocus();
            }
            return;
        }

        emptyView.setVisibility(View.GONE);
        appsScroll.setVisibility(section == SECTION_HOME ? View.VISIBLE : View.GONE);
        pills.setVisibility(section == SECTION_HOME ? View.VISIBLE : View.GONE);

        int gap = Dp.of(AppCard.GAP_DP);
        for (int i = 0; i < apps.size(); i++) {
            AppCard card = new AppCard(this);
            card.bind(apps.get(i));
            card.setId(View.generateViewId());
            card.setOnFocusChangeListener((v, has) -> {
                AppCard c = (AppCard) v;
                c.applyFocus(has);
                if (has) {
                    onCardFocused(c);
                }
            });
            card.setOnClickListener(v -> onCardChosen((AppCard) v));
            card.setOnLongClickListener(v -> {
                openAppMenu((AppCard) v);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Dp.of(AppCard.WIDTH_DP), Dp.of(AppCard.HEIGHT_DP));
            lp.setMarginEnd(i == apps.size() - 1 ? 0 : gap);
            appsRow.addView(card, lp);
        }
        lockRow(appsRow, railHome.getId());
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            appsRow.getChildAt(i).setNextFocusUpId(openPill.getId());
        }
        openPill.setNextFocusLeftId(railHome.getId());

        // An app installed or removed while the grid is open has to show up
        // there too, not only in the row behind it.
        if (section == SECTION_APPS) {
            appsSection.bind(apps);
            View target = appsSection.cardFor(currentApp == null ? null : currentApp.pkg);
            if (target != null) {
                target.requestFocus();
            }
            return;
        }

        restoreFocus();
    }

    /**
     * Keep left and right inside one row.
     *
     * Without this the focus finder is free to pick any view that happens to
     * lie in the direction pressed, whatever row it is in. Pressing right on
     * the last chip used to jump up to an app, and the next Select opened it.
     * The far left of every row escapes to the rail instead, which is the one
     * place left should ever go.
     */
    private void lockRow(ViewGroup row, int leftEscapeId) {
        int n = row.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = row.getChildAt(i);
            v.setNextFocusLeftId(i == 0 ? leftEscapeId : row.getChildAt(i - 1).getId());
            v.setNextFocusRightId(row.getChildAt(i == n - 1 ? n - 1 : i + 1).getId());
        }
    }

    /**
     * Put focus back where the user left it.
     *
     * Coming back from an app and finding the row reset to the beginning is one
     * of the most irritating things a TV home screen can do, so the last
     * focused app is remembered across launches as well as across restarts.
     */
    private void restoreFocus() {
        String wanted = prefs.lastFocus();
        View target = null;
        if (wanted != null) {
            for (int i = 0; i < appsRow.getChildCount(); i++) {
                AppCard c = (AppCard) appsRow.getChildAt(i);
                if (c.entry() != null && wanted.equals(c.entry().pkg)) {
                    target = c;
                    break;
                }
            }
        }
        if (target == null && appsRow.getChildCount() > 0) {
            target = appsRow.getChildAt(0);
        }
        if (target == null && toolsRow.getChildCount() > 0) {
            target = toolsRow.getChildAt(0);
        }
        if (target != null) {
            target.requestFocus();
        }
    }

    private void onCardFocused(AppCard card) {
        focusedCard = card;
        AppEntry e = card.entry();
        if (e == null) {
            return;
        }
        currentApp = e;
        aimVerticalMovesAt(card);
        if (arranging) {
            hero.show(getString(R.string.row_apps), e.label,
                    getString(R.string.arrange_hint), e.accent);
        } else {
            refreshHero();
        }
        showFieldFor(e);
        railHome.setAccent(Palette.asRim(accentOfCurrent()));
        railApps.setAccent(Palette.asRim(accentOfCurrent()));
        railSettings.setAccent(Palette.asRim(accentOfCurrent()));
    }

    /**
     * Point every way back into the row at the tile the user is actually on.
     *
     * Leaving the row upwards and coming back down to the first tile again is
     * the single most irritating thing a TV layout does. The nearest view in
     * the direction pressed is not what a person means. They mean "back to
     * where I was".
     */
    private void aimVerticalMovesAt(AppCard card) {
        if (card.getParent() != appsRow) {
            return;
        }
        openPill.setNextFocusDownId(card.getId());
        optionsPill.setNextFocusDownId(card.getId());
        for (int i = 0; i < toolsRow.getChildCount(); i++) {
            toolsRow.getChildAt(i).setNextFocusUpId(card.getId());
        }
    }

    /**
     * Put this app's room on the screen.
     *
     * If it has already been generated this is immediate. If it has not, the
     * room on screen stays until the new one is ready, which is why fast
     * scrubbing along the row never flashes or stutters.
     */
    private void showFieldFor(final AppEntry e) {
        Bitmap have = fields.request(e.pkg, specFor(e), (key, bmp) -> {
            if (currentApp != null && currentApp.pkg.equals(key)) {
                field.show(bmp, true);
            }
        });
        if (have != null) {
            field.show(have, true);
        }
    }

    private void refreshHero() {
        if (currentApp == null || apps.isEmpty()) {
            return;
        }
        hero.show(timeWord(), currentApp.label,
                Since.describe(this, currentApp.lastOpened, System.currentTimeMillis()),
                accentOfCurrent());
    }

    private int accentOfCurrent() {
        if (currentApp == null) {
            return light().key;
        }
        return prefs.accentFromApp() && currentApp.accent != 0 ? currentApp.accent : light().key;
    }

    /** The word above the name, which is just what time it is. */
    private String timeWord() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) {
            return getString(R.string.hero_morning);
        }
        if (h >= 12 && h < 17) {
            return getString(R.string.hero_afternoon);
        }
        if (h >= 17 && h < 21) {
            return getString(R.string.hero_evening);
        }
        return getString(R.string.hero_night);
    }

    private void onCardChosen(AppCard card) {
        if (arranging) {
            exitArrangeMode();
            return;
        }
        card.playPress();
        open(card.entry());
    }

    private void openCurrentApp() {
        openPill.animate().cancel();
        open(currentApp);
    }

    private void open(AppEntry e) {
        if (e == null) {
            return;
        }
        prefs.setLastFocus(e.pkg);
        long now = System.currentTimeMillis();
        if (Handoff.launch(this, e.component)) {
            // Recorded here, by us, when we started it. This is the only
            // honest "recent" a launcher on this TV has.
            prefs.noteOpened(e.pkg, now);
            e.lastOpened = now;
            return;
        }
        hero.show(timeWord(), e.label, getString(R.string.cannot_open), e.accent);
        handler.postDelayed(() -> refreshHero(), 2400);
    }

    // ---- The app menu ---------------------------------------------------

    private void openAppMenu(final AppCard card) {
        final AppEntry e = card.entry();
        if (e == null || arranging) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.menu_pin));
        options.add(getString(R.string.menu_rearrange));
        options.add(getString(R.string.menu_hide));
        options.add(getString(R.string.menu_app_info));

        focusBeforeSheet = getCurrentFocus();
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
                        enterArrangeMode(card);
                        break;
                    case 2:
                        hideApp(e);
                        break;
                    default:
                        Handoff.openAppInfo(AtriumActivity.this, e.pkg);
                        break;
                }
            }

            @Override
            public void onSheetClosed() {
                returnFocus();
            }
        });
        sheet.open(0);
    }

    /** Give focus back to whatever had it before the menu opened. */
    private void returnFocus() {
        handler.post(() -> {
            // Choosing "hidden apps" closes one menu and opens another in the
            // same breath. Without this check the closing menu would pull focus
            // out of the one that just opened.
            if (sheet.isOpen()) {
                return;
            }
            if (focusBeforeSheet != null && focusBeforeSheet.getParent() != null
                    && focusBeforeSheet.isFocusable()) {
                focusBeforeSheet.requestFocus();
                focusBeforeSheet = null;
                return;
            }
            focusBeforeSheet = null;
            if (focusedCard != null && focusedCard.getParent() != null) {
                focusedCard.requestFocus();
            } else {
                restoreFocus();
            }
        });
    }

    private void hideApp(AppEntry e) {
        repo.hide(e.pkg);
        prefs.setLastFocus(null);
        loadApps();
    }

    // ---- Rearranging ----------------------------------------------------

    private void enterArrangeMode(AppCard card) {
        if (section == SECTION_APPS) {
            showSection(SECTION_HOME, false);
        }
        arranging = true;
        AppCard target = cardInRowFor(card);
        if (target == null) {
            arranging = false;
            return;
        }
        target.setArrangeMode(true);
        target.requestFocus();
        focusedCard = target;
        hero.show(getString(R.string.row_apps), target.entry().label,
                getString(R.string.arrange_hint), target.entry().accent);
    }

    private AppCard cardInRowFor(AppCard card) {
        if (card != null && card.getParent() == appsRow) {
            return card;
        }
        String pkg = card == null || card.entry() == null ? null : card.entry().pkg;
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            AppCard c = (AppCard) appsRow.getChildAt(i);
            if (c.entry() != null && c.entry().pkg.equals(pkg)) {
                return c;
            }
        }
        return appsRow.getChildCount() > 0 ? (AppCard) appsRow.getChildAt(0) : null;
    }

    private void exitArrangeMode() {
        arranging = false;
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            ((AppCard) appsRow.getChildAt(i)).setArrangeMode(false);
        }
        repo.saveOrder(apps);
        refreshHero();
    }

    /** Swap the focused app with its neighbour and keep focus on it. */
    private boolean moveFocused(int delta) {
        if (focusedCard == null) {
            return false;
        }
        int from = appsRow.indexOfChild(focusedCard);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= appsRow.getChildCount()) {
            return true;    // consumed: do not let focus walk away mid-move
        }
        Collections.swap(apps, from, to);

        AppCard card = focusedCard;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        appsRow.removeView(card);
        appsRow.addView(card, to, lp);
        fixRowMargins();
        lockRow(appsRow, railHome.getId());
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            appsRow.getChildAt(i).setNextFocusUpId(openPill.getId());
        }
        card.requestFocus();
        return true;
    }

    /** Only the last tile has no trailing gap, so margins are redone after a move. */
    private void fixRowMargins() {
        int gap = Dp.of(AppCard.GAP_DP);
        int n = appsRow.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = appsRow.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            lp.setMarginEnd(i == n - 1 ? 0 : gap);
            v.setLayoutParams(lp);
        }
    }

    // ---- Preferences ----------------------------------------------------

    private void openPreferences() {
        // Order matters. Rearranging and hiding apps are what people come
        // looking for, so they are at the top. Holding Select on an app does
        // the same thing, but nobody finds that on their own.
        final List<String> options = new ArrayList<>();
        options.add(getString(R.string.prefs_rearrange));
        options.add(getString(R.string.prefs_hidden));
        options.add(getString(R.string.prefs_light));
        options.add(getString(R.string.prefs_accent));
        options.add(getString(R.string.prefs_motion));
        options.add(getString(R.string.prefs_clock));
        options.add(getString(R.string.prefs_replace_home));

        focusBeforeSheet = getCurrentFocus();
        sheet.configure(getString(R.string.prefs_title), options, new Sheet.Callback() {
            @Override
            public void onSheetChoice(int index) {
                switch (index) {
                    case 0:
                        sheet.close();
                        startRearranging();
                        return;
                    case 1:
                        sheet.close();
                        openHiddenApps();
                        return;
                    case 2:
                        cycleLight();
                        break;
                    case 3:
                        prefs.setAccentFromApp(!prefs.accentFromApp());
                        relight();
                        break;
                    case 4:
                        prefs.setReduceMotion(!prefs.reduceMotion());
                        Motion.setReduced(prefs.reduceMotion());
                        break;
                    case 5:
                        prefs.setClock24(!prefs.clock24());
                        clock.setUse24(prefs.clock24());
                        break;
                    default:
                        boolean on = !prefs.replaceHome();
                        prefs.setReplaceHome(on);
                        if (on) {
                            WakeService.ensureRunning(AtriumActivity.this);
                        } else {
                            WakeService.stop(AtriumActivity.this);
                        }
                        buildTools();
                        break;
                }
                updatePreferenceValues();
            }

            @Override
            public void onSheetClosed() {
                returnFocus();
            }
        });
        updatePreferenceValues();
        sheet.open(0);
    }

    private void startRearranging() {
        handler.post(() -> {
            AppCard target = cardInRowFor(focusedCard);
            if (target == null) {
                return;
            }
            enterArrangeMode(target);
        });
    }

    /** Each preference row shows its current setting, so nothing is hidden. */
    private void updatePreferenceValues() {
        String[] lightNames = {
                getString(R.string.prefs_light_auto),
                getString(R.string.prefs_light_warm),
                getString(R.string.prefs_light_cool),
                getString(R.string.prefs_light_mono)};
        setRowValue(0, String.valueOf(apps.size()));
        int hidden = prefs.hidden().size();
        setRowValue(1, hidden == 0 ? getString(R.string.prefs_none_hidden) : String.valueOf(hidden));
        setRowValue(2, lightNames[Math.max(0, Math.min(3, prefs.light()))]);
        setRowValue(3, onOff(prefs.accentFromApp()));
        setRowValue(4, onOff(prefs.reduceMotion()));
        setRowValue(5, prefs.clock24() ? "24" : "12");
        setRowValue(6, onOff(prefs.replaceHome()));
    }

    private void setRowValue(int index, String value) {
        dev.skhanal.atrium.ui.SheetRow row = sheet.rowAt(index);
        if (row != null) {
            row.setValue(value);
        }
    }

    private String onOff(boolean on) {
        return getString(on ? R.string.prefs_on : R.string.prefs_off);
    }

    private void cycleLight() {
        prefs.setLight((prefs.light() + 1) % 4);
        relight();
    }

    private Palette.Light light() {
        int hour = clock == null ? Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                : clock.hourOfDay();
        lastHour = hour;
        return Palette.lightFor(prefs.light(), hour);
    }

    /**
     * Rebuild every room, because the light they were generated under changed.
     *
     * The one on screen stays until its replacement is ready, so changing the
     * setting never shows a blank screen.
     */
    private void relight() {
        fields.clear();
        field.setFallback(light().baseBottom);
        if (currentApp != null) {
            showFieldFor(currentApp);
        }
        buildTools();
        warmFields();
    }

    private void openHiddenApps() {
        final List<AppEntry> hidden = repo.hiddenApps();
        final List<String> options = new ArrayList<>();
        for (AppEntry e : hidden) {
            options.add(e.label);
        }
        if (!hidden.isEmpty()) {
            options.add(getString(R.string.prefs_restore));
        } else {
            options.add(getString(R.string.prefs_none_hidden));
        }

        sheet.configure(getString(R.string.prefs_hidden), options, new Sheet.Callback() {
            @Override
            public void onSheetChoice(int index) {
                if (hidden.isEmpty()) {
                    sheet.close();
                    return;
                }
                if (index < hidden.size()) {
                    repo.unhide(hidden.get(index).pkg);
                } else {
                    repo.unhideAll();
                }
                sheet.close();
                loadApps();
            }

            @Override
            public void onSheetClosed() {
                returnFocus();
            }
        });
        // Two different system apps on this TV are both called "Downloads", so
        // the name alone does not say which row is which. The package name goes
        // in the column on the right, which was empty here anyway.
        for (int i = 0; i < hidden.size(); i++) {
            dev.skhanal.atrium.ui.SheetRow row = sheet.rowAt(i);
            if (row != null) {
                row.setValue(hidden.get(i).pkg);
            }
        }
        sheet.open(0);
    }

    // ---- Keys -----------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (sheet.isOpen()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    sheet.close();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // While an app is being moved these keys are consumed whole, both the
        // press and the release. Consuming only the press would let the release
        // of the Select that ends the move reach the app underneath and open
        // it, which is the opposite of what the user asked for.
        if (arranging) {
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return !down || moveFocused(-1);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return !down || moveFocused(1);
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (!down) {
                        exitArrangeMode();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // Leaving the row half way through a move would strand it.
                    return true;
                default:
                    break;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View current = getCurrentFocus();

            // Right out of the rail goes back to whatever was focused in the
            // content, not to whatever happens to be nearest on screen.
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT
                    && current instanceof RailItem) {
                View target = contentFocusTarget();
                if (target != null) {
                    target.requestFocus();
                    return true;
                }
            }

            // The Menu button is a shortcut to the app menu, but only when an
            // app is what is actually focused.
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && current instanceof AppCard) {
                openAppMenu((AppCard) current);
                return true;
            }
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Back leaves the Apps section. On the home screen it has nowhere
            // to go, so it is swallowed rather than dropping the user onto a
            // black screen.
            if (event.getAction() == KeyEvent.ACTION_UP && section != SECTION_HOME) {
                showSection(SECTION_HOME, true);
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private View contentFocusTarget() {
        if (section == SECTION_APPS) {
            return appsSection.cardFor(currentApp == null ? null : currentApp.pkg);
        }
        if (focusedCard != null && focusedCard.getParent() == appsRow) {
            return focusedCard;
        }
        if (appsRow.getChildCount() > 0) {
            return appsRow.getChildAt(0);
        }
        return toolsRow.getChildCount() > 0 ? toolsRow.getChildAt(0) : null;
    }

    @Override
    public void onBackPressed() {
        // Deliberately empty. See dispatchKeyEvent.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already here should settle the screen, not
        // rebuild it.
        if (arranging) {
            exitArrangeMode();
        }
        if (sheet.isOpen()) {
            sheet.close();
        }
        if (section != SECTION_HOME) {
            showSection(SECTION_HOME, true);
        } else {
            returnFocus();
        }
    }

    // ---- Receivers ------------------------------------------------------

    private void updateNotice() {
        home.setNotice(Handoff.isOnline(this) ? null : getString(R.string.offline));
    }

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            clock.update();
            clock.invalidate();
            updateNotice();
            // The room follows the hour, so at the turn of the hour the light
            // may need to change with it.
            int hour = clock.hourOfDay();
            if (hour != lastHour && prefs.light() == Palette.LIGHT_AUTO) {
                relight();
            }
            lastHour = hour;
        }
    };

    /**
     * Installing or updating one app fires several broadcasts in a row. They
     * are collapsed into a single reload so the row is not rebuilt four times,
     * which would throw away the user's focus each time.
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
