package dev.skhanal.atrium.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import dev.skhanal.atrium.content.HomeFeed;
import dev.skhanal.atrium.content.StreamingApps;
import dev.skhanal.atrium.content.Title;
import dev.skhanal.atrium.content.TvApp;
import dev.skhanal.atrium.content.WatchOption;
import dev.skhanal.atrium.content.WatchText;
import dev.skhanal.atrium.data.AppEntry;

/**
 * The home screen: the top bar, the hero, and the rows.
 *
 * The hero follows the remote. On a movie or show it shows that title and its
 * artwork. Above the content rows, where there is nothing to describe, it
 * shows the week's top five trending titles in turn.
 *
 * Rows, top to bottom in the focus grid: the top bar, the hero buttons, your
 * apps, then one row per content row in the feed.
 */
public final class HomeScreen extends FrameLayout {

    /** The left and right margin of everything. A TV can crop up to 5% of each edge, which is 48dp. */
    static final float MARGIN_DP = 48f;
    private static final float HERO_TOP_DP = 72f;
    private static final float ROWS_TOP_DP = 342f;
    private static final int TRENDING_IN_HERO = 5;
    /** Moving along a row quickly should not load every picture it passes. */
    private static final long HERO_SETTLE_MS = 180;

    static final int ROW_NAV = 0;
    static final int ROW_HERO = 1;
    static final int ROW_APPS = 2;

    /** What the screen asks the activity to do. */
    public interface Actions {
        void openApp(AppEntry app);

        void openTitle(Title title);

        void playTrailer(Title title);
    }

    public final TopBar top;
    private final HeroView hero;
    private final LinearLayout rows;
    private final Backdrop backdrop;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RowView appsRow;
    private final List<RowView> contentRows = new ArrayList<>();
    private List<Title> rotation = Collections.emptyList();
    private int rotationIndex;
    private boolean rotating;
    private Title heroTitle;
    private Set<String> installed = Collections.emptySet();
    private long feedSavedAt = -1;
    /** The title the hero shows once the remote rests on it. */
    private Title pendingTitle;
    private String pendingEyebrow;
    private final Runnable settle = () -> showTitle(pendingTitle, pendingEyebrow);

    public HomeScreen(Context context, Backdrop backdrop, Actions actions) {
        super(context);
        this.backdrop = backdrop;
        this.actions = actions;
        setClipChildren(false);

        top = new TopBar(context);
        LayoutParams tp = new LayoutParams(LayoutParams.MATCH_PARENT, Dp.of(36f));
        tp.leftMargin = Dp.of(MARGIN_DP - 14f);   // the first item's own padding lines its text up with the margin
        tp.rightMargin = Dp.of(MARGIN_DP);
        tp.topMargin = Dp.of(20f);
        addView(top, tp);

        hero = new HeroView(context);
        hero.info.set("More info", Chip.Icon.INFO, () -> {
            if (heroTitle != null) {
                actions.openTitle(heroTitle);
            }
        });
        hero.trailer.set("Trailer", Chip.Icon.PLAY, () -> {
            if (heroTitle != null) {
                actions.playTrailer(heroTitle);
            }
        });
        LayoutParams hp = new LayoutParams(Dp.of(HeroView.WIDTH_DP), LayoutParams.WRAP_CONTENT);
        hp.leftMargin = Dp.of(MARGIN_DP);
        hp.topMargin = Dp.of(HERO_TOP_DP);
        hero.setButtonsVisible(false);
        addView(hero, hp);

        // The rows scroll up inside this frame, which clips them below the hero.
        FrameLayout viewport = new Viewport(context);
        LayoutParams vp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        vp.topMargin = Dp.of(ROWS_TOP_DP);
        addView(viewport, vp);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setPadding(0, Dp.of(6f), 0, 0);
        viewport.addView(rows, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * The area the rows scroll in.
     *
     * Its rows are measured at their full height, not cut to the frame, and
     * drawing is clipped to the frame by hand. Android's own clipping is
     * switched off further down so a focused card can grow over its
     * neighbours, and with it off the rows drew over the hero.
     */
    private static final class Viewport extends FrameLayout {
        Viewport(Context c) {
            super(c);
        }

        @Override
        protected void measureChildWithMargins(View child, int widthSpec, int widthUsed,
                int heightSpec, int heightUsed) {
            super.measureChildWithMargins(child, widthSpec, widthUsed,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed);
        }

        @Override
        protected void dispatchDraw(android.graphics.Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }

    // ---- Content --------------------------------------------------------

    /** Rebuild the apps row. Returns the tiles in order. */
    public List<AppTile> setApps(List<AppEntry> apps) {
        if (appsRow != null) {
            rows.removeView(appsRow);
        }
        appsRow = new RowView(getContext(), "Your apps", null);
        List<AppTile> tiles = new ArrayList<>();
        for (AppEntry e : apps) {
            AppTile t = new AppTile(getContext());
            t.bind(e, () -> actions.openApp(e));
            appsRow.add(t);
            tiles.add(t);
        }
        rows.addView(appsRow, 0);
        return tiles;
    }

    /**
     * Rebuild the content rows from a feed. A feed already shown is ignored,
     * so coming back to the home screen keeps every row where it was.
     *
     * @return true when the rows changed
     */
    public boolean setFeed(HomeFeed feed, Set<String> installedApps) {
        if (feed.savedAt == feedSavedAt) {
            return false;
        }
        feedSavedAt = feed.savedAt;
        installed = installedApps;
        for (RowView r : contentRows) {
            rows.removeView(r);
        }
        contentRows.clear();
        for (java.util.Map.Entry<HomeFeed.Row, List<Title>> e : feed.rows.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            RowView row = rowFor(e.getKey());
            boolean poster = e.getKey() == HomeFeed.Row.POPULAR_SHOWS;
            for (Title t : e.getValue()) {
                TitleCard card = new TitleCard(getContext(), poster);
                WatchOption here = here(t);
                card.bind(t, here == null ? null : here.logo, () -> actions.openTitle(t));
                row.add(card);
            }
            contentRows.add(row);
            rows.addView(row);
        }
        List<Title> trending = feed.rows.get(HomeFeed.Row.TRENDING);
        rotation = trending == null ? Collections.<Title>emptyList()
                : trending.subList(0, Math.min(TRENDING_IN_HERO, trending.size()));
        if (rotating || heroTitle == null) {
            rotationIndex = 0;
            showRotation();
        }
        return true;
    }

    private RowView rowFor(HomeFeed.Row row) {
        switch (row) {
            case TRENDING:
                return new RowView(getContext(), "Trending this week", null);
            case ON_YOUR_APPS:
                return new RowView(getContext(), "On your apps", appsNote());
            default:
                return new RowView(getContext(), "Popular shows", null);
        }
    }

    /** "Peacock and Apple TV": the streaming apps this row is drawn from. */
    private String appsNote() {
        List<String> names = new ArrayList<>();
        for (TvApp a : StreamingApps.subscriptionApps(installed)) {
            names.add(a.name);
        }
        if (names.isEmpty()) {
            return null;
        }
        String last = names.remove(names.size() - 1);
        return names.isEmpty() ? last : android.text.TextUtils.join(", ", names) + " and " + last;
    }

    private WatchOption here(Title t) {
        return t.details == null ? null : StreamingApps.watchHere(t.details.offers, installed);
    }

    /** Every row the remote can reach, top to bottom, in focus-grid order. */
    public List<List<Focusable>> focusRows() {
        List<List<Focusable>> out = new ArrayList<>();
        out.add(new ArrayList<Focusable>(top.items()));
        List<Focusable> buttons = new ArrayList<>();
        if (heroTitle != null) {
            buttons.add(hero.info);
            buttons.add(hero.trailer);
        }
        out.add(buttons);
        out.add(appsRow == null ? new ArrayList<Focusable>() : asFocusable(appsRow.items()));
        for (RowView r : contentRows) {
            out.add(asFocusable(r.items()));
        }
        return out;
    }

    private static List<Focusable> asFocusable(List<View> views) {
        List<Focusable> out = new ArrayList<>();
        for (View v : views) {
            out.add((Focusable) v);
        }
        return out;
    }

    public RowView appsRow() {
        return appsRow;
    }

    // ---- Focus ------------------------------------------------------------

    /**
     * Focus moved to (row, column). Slide that row so the item shows, scroll
     * the rows so the focused one sits under the hero, and update the hero.
     */
    public void onFocus(int row, int column, Focusable item) {
        RowView rv = row == ROW_APPS ? appsRow : row > ROW_APPS ? contentRows.get(row - ROW_APPS - 1) : null;
        if (rv != null) {
            rv.reveal(column);
        }
        scrollRows(row);
        handler.removeCallbacks(settle);
        if (item instanceof AppTile && rotation.isEmpty()) {
            // No movie data yet, offline on a first start or a build without
            // a token. The hero names the app instead of standing empty.
            showApp(((AppTile) item).entry());
        } else if (item instanceof TitleCard) {
            stopRotation();
            pendingTitle = ((TitleCard) item).title();
            pendingEyebrow = eyebrowFor(pendingTitle);
            handler.postDelayed(settle, HERO_SETTLE_MS);
        } else if (row == ROW_HERO) {
            // Reading the hero: hold still.
            stopRotation();
        } else {
            startRotation();
        }
    }

    private void scrollRows(int row) {
        // The apps row sits under the hero. Any row below it moves up into
        // the apps row's place, so the focused row is always fully on screen.
        int index = row - ROW_APPS;
        float y = 0;
        if (appsRow != null && index >= 1 && index - 1 < contentRows.size()) {
            y = -contentRows.get(index - 1).getTop() + appsRow.getTop();
        }
        rows.animate().cancel();
        long d = Motion.d(Motion.SECTION);
        if (d == 0) {
            rows.setTranslationY(y);
        } else {
            rows.animate().translationY(y).setDuration(d).setInterpolator(Motion.SWIFT).start();
        }
    }

    private String eyebrowFor(Title t) {
        String kind = t.kind == Title.Kind.TV ? "Show" : "Movie";
        WatchOption w = here(t);
        return w == null ? kind : kind + "  ·  " + WatchText.watchHere(w);
    }

    // ---- Hero -------------------------------------------------------------

    private void showApp(AppEntry e) {
        heroTitle = null;
        hero.setButtonsVisible(false);
        hero.show("Your apps", e.label, Collections.<String>emptyList(), "", null, false);
    }

    private void showTitle(Title t, String eyebrow) {
        if (t == null) {
            return;
        }
        heroTitle = t;
        hero.setButtonsVisible(true);
        WatchOption w = here(t);
        hero.show(eyebrow, t.name, WatchText.facts(t), t.details == null ? "" : t.details.overview,
                w == null ? null : WatchText.watchHere(w) + ", on this TV",
                t.details != null && t.details.trailer != null);
        backdrop.showArtwork(t.backdrop);
    }

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            rotationIndex = (rotationIndex + 1) % Math.max(1, rotation.size());
            showRotation();
            handler.postDelayed(this, Motion.HERO_ROTATE);
        }
    };

    private void showRotation() {
        if (rotation.isEmpty()) {
            hero.setPage(-1, 0);
            return;
        }
        showTitle(rotation.get(rotationIndex), "#" + (rotationIndex + 1) + " trending this week");
        hero.setPage(rotationIndex, rotation.size());
        // Load only the next picture ahead of its turn. Loading all five held
        // about 10 MB for pictures that may never be shown.
        backdrop.preload(rotation.get((rotationIndex + 1) % rotation.size()).backdrop);
    }

    private void startRotation() {
        if (rotating) {
            return;
        }
        rotating = true;
        if (!rotation.contains(heroTitle)) {
            showRotation();
        } else {
            hero.setPage(rotationIndex, rotation.size());
        }
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, Motion.HERO_ROTATE);
    }

    private void stopRotation() {
        rotating = false;
        handler.removeCallbacks(rotate);
        hero.setPage(-1, 0);
    }

    /** Stop the timers while the screen is hidden or the app is in the background. */
    public void pause() {
        handler.removeCallbacks(rotate);
        handler.removeCallbacks(settle);
        rotating = false;
    }

    /** Put the hero's picture back after the detail screen showed another. */
    public void restoreBackdrop() {
        if (heroTitle != null) {
            backdrop.showArtwork(heroTitle.backdrop);
        }
    }
}
