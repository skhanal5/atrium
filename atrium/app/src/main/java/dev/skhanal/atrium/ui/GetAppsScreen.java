package dev.skhanal.atrium.ui;

import android.content.Context;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import dev.skhanal.atrium.content.AppCatalog;
import dev.skhanal.atrium.content.TvApp;

/**
 * Get apps: a search field and a grid of popular apps, each with a checked
 * Amazon Appstore page. Typing filters the grid. The last card opens the
 * Appstore itself, for any app not in the grid.
 *
 * Rows for the remote: the search field, then each row of the grid.
 */
public final class GetAppsScreen extends FrameLayout {

    private static final int COLUMNS = 7;
    private static final float GAP_DP = 10f;

    public interface Actions {
        void getApp(TvApp app);

        void openAppstore();
    }

    public final SearchBox search;
    private final LinearLayout grid;
    private final TextView empty;
    private final Actions actions;
    private final List<List<Focusable>> gridRows = new ArrayList<>();
    private Set<String> installed = Collections.emptySet();

    public GetAppsScreen(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setClipChildren(false);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        LayoutParams cp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Dp.of(HomeScreen.MARGIN_DP);
        cp.rightMargin = Dp.of(HomeScreen.MARGIN_DP);
        cp.topMargin = Dp.of(72f);
        addView(column, cp);

        TextView title = text(context, "Get apps", Type.bold(), 34f, Colors.TEXT);
        title.setLetterSpacing(-0.02f);
        column.addView(title);
        column.addView(text(context, "Pick an app to open its Amazon Appstore page. Installs and updates happen there.",
                Type.regular(), 12f, Colors.TEXT_2), margins(6f));

        search = new SearchBox(context);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Dp.of(420f), Dp.of(38f));
        sp.topMargin = Dp.of(16f);
        column.addView(search, sp);

        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        column.addView(grid, margins(18f));

        empty = text(context, "No app in this list matches. The last card searches the whole Appstore.",
                Type.regular(), 11f, Colors.TEXT_3);
        column.addView(empty, margins(18f));
    }

    private static TextView text(Context c, String s, android.graphics.Typeface face, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dp.sp(sp));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static LinearLayout.LayoutParams margins(float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Dp.of(topDp);
        return lp;
    }

    /** Which apps are on the TV, so their cards say Installed. */
    public void setInstalled(Set<String> packages) {
        installed = packages;
        show(search.text());
    }

    /** Fill the grid with the apps that match, then the Appstore card. */
    public void show(String query) {
        grid.removeAllViews();
        gridRows.clear();
        List<TvApp> found = AppCatalog.search(query);
        empty.setVisibility(found.isEmpty() ? VISIBLE : GONE);
        List<CatalogCard> cards = new ArrayList<>();
        for (TvApp app : found) {
            CatalogCard c = new CatalogCard(getContext());
            c.bind(app, app.installedIn(installed), () -> actions.getApp(app));
            cards.add(c);
        }
        CatalogCard store = new CatalogCard(getContext());
        store.bindAppstore(actions::openAppstore);
        cards.add(store);

        LinearLayout row = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(getContext());
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rp.topMargin = Dp.of(GAP_DP);
                }
                grid.addView(row, rp);
                gridRows.add(new ArrayList<Focusable>());
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i % COLUMNS != 0) {
                lp.setMarginStart(Dp.of(GAP_DP));
            }
            row.addView(cards.get(i), lp);
            gridRows.get(gridRows.size() - 1).add(cards.get(i));
        }
    }

    /** The search field, then each grid row. */
    public List<List<Focusable>> focusRows() {
        List<List<Focusable>> out = new ArrayList<>();
        out.add(Collections.<Focusable>singletonList(search));
        out.addAll(gridRows);
        return out;
    }
}
