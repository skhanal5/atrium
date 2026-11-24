package dev.skhanal.atrium.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.skhanal.atrium.content.Details;
import dev.skhanal.atrium.content.FeedFetcher;
import dev.skhanal.atrium.content.StreamingApps;
import dev.skhanal.atrium.content.Title;
import dev.skhanal.atrium.content.WatchOption;
import dev.skhanal.atrium.content.WatchText;

/**
 * Everything about one movie or show, and where to watch it on this TV.
 *
 * Two rows for the remote: the buttons, then the places to watch. Apps on the
 * TV come first and say "Open". Apps you do not have say "Get the app".
 */
public final class DetailScreen extends FrameLayout {

    /** At most this many places to watch, so the row fits the screen without scrolling. */
    private static final int MAX_OPTIONS = 5;

    public interface Actions {
        void playTrailer(Title title);

        void watch(WatchOption option);

        void close();
    }

    private final LinearLayout column;
    private final Actions actions;
    private final List<Focusable> buttons = new ArrayList<>();
    private final List<Focusable> options = new ArrayList<>();

    public DetailScreen(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setClipChildren(false);
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Dp.of(HomeScreen.MARGIN_DP);
        lp.rightMargin = Dp.of(HomeScreen.MARGIN_DP);
        lp.topMargin = Dp.of(64f);
        addView(column, lp);
    }

    public void show(Title t, Set<String> installed) {
        column.removeAllViews();
        buttons.clear();
        options.clear();
        Details d = t.details;

        TextView kind = text(t.kind == Title.Kind.TV ? "TV show" : "Movie", Type.semibold(), 10f, Colors.TEXT_2);
        kind.setLetterSpacing(0.14f);
        kind.setAllCaps(true);
        column.addView(kind);

        TextView name = text(t.name, Type.forLabel(t.name, Type.bold()), t.name.length() <= 26 ? 46f : 34f, Colors.TEXT);
        name.setLetterSpacing(-0.02f);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(name, margins(7f, 0f));

        if (d != null && !d.tagline.isEmpty()) {
            TextView tag = text(d.tagline, Typeface.create(Type.regular(), Typeface.ITALIC), 13f, Colors.TEXT_2);
            column.addView(tag, margins(6f, 0f));
        }
        // Genres have their own chips below, so the facts line leaves them out.
        List<String> facts = WatchText.facts(t, false);
        if (!facts.isEmpty()) {
            column.addView(text(TextUtils.join("  ·  ", facts), Type.medium(), 11.5f, Colors.TEXT_2), margins(10f, 0f));
        }
        if (d != null && !d.genres.isEmpty()) {
            LinearLayout g = new LinearLayout(getContext());
            for (String genre : d.genres) {
                TextView chip = text(genre, Type.semibold(), 9.5f, Colors.TEXT_2);
                chip.setPadding(Dp.of(8f), Dp.of(3f), Dp.of(8f), Dp.of(3f));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Colors.SURFACE);
                bg.setCornerRadius(Dp.f(20f));
                chip.setBackground(bg);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.setMarginEnd(Dp.of(5f));
                g.addView(chip, cp);
            }
            column.addView(g, margins(9f, 0f));
        }
        if (d != null && !d.overview.isEmpty()) {
            TextView about = text(d.overview, Type.regular(), 12.5f, Colors.TEXT_2);
            about.setMaxLines(4);
            about.setEllipsize(TextUtils.TruncateAt.END);
            about.setLineSpacing(0f, 1.4f);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Dp.of(480f),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ap.topMargin = Dp.of(12f);
            column.addView(about, ap);
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setClipChildren(false);
        Chip trailer = new Chip(getContext(), Chip.Style.BUTTON)
                .set("Watch trailer", Chip.Icon.PLAY, () -> actions.playTrailer(t));
        trailer.setChipEnabled(d != null && d.trailer != null);
        Chip back = new Chip(getContext(), Chip.Style.BUTTON).set("Back", Chip.Icon.NONE, actions::close);
        row.addView(trailer);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMarginStart(Dp.of(9f));
        row.addView(back, bp);
        column.addView(row, margins(16f, 0f));
        buttons.add(trailer);
        buttons.add(back);

        TextView where = text("Where to watch", Type.bold(), 12.5f, Colors.TEXT);
        TextView country = text("United States", Type.medium(), 10f, Colors.TEXT_3);
        LinearLayout heading = new LinearLayout(getContext());
        heading.setGravity(android.view.Gravity.BOTTOM);
        heading.addView(where);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMarginStart(Dp.of(8f));
        heading.addView(country, cp);
        column.addView(heading, margins(22f, 9f));

        List<WatchOption> found = d == null ? new ArrayList<WatchOption>()
                : StreamingApps.whereToWatch(d.offers, installed);
        if (found.isEmpty()) {
            column.addView(text("TMDB lists no " + FeedFetcher.COUNTRY
                    + " streaming service for this title yet.", Type.regular(), 11f, Colors.TEXT_3));
            return;
        }
        LinearLayout cards = new LinearLayout(getContext());
        cards.setClipChildren(false);
        for (WatchOption w : found.subList(0, Math.min(MAX_OPTIONS, found.size()))) {
            OptionCard card = new OptionCard(getContext());
            card.bind(w, () -> actions.watch(w));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (!options.isEmpty()) {
                lp.setMarginStart(Dp.of(11f));
            }
            cards.addView(card, lp);
            options.add(card);
        }
        column.addView(cards);
    }

    /** The buttons, then the places to watch. */
    public List<List<Focusable>> focusRows() {
        List<List<Focusable>> out = new ArrayList<>();
        out.add(buttons);
        out.add(options);
        return out;
    }

    private TextView text(String s, Typeface face, float sp, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(sp));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static LinearLayout.LayoutParams margins(float topDp, float bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Dp.of(topDp);
        lp.bottomMargin = Dp.of(bottomDp);
        return lp;
    }
}
