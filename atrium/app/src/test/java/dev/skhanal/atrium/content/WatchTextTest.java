package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WatchTextTest {

    private static WatchOption option(String app, boolean installed, Offer.Mode... modes) {
        TvApp a = StreamingApps.ALL.stream().filter(x -> x.name.equals(app)).findFirst().get();
        return new WatchOption(a, Arrays.asList(modes), installed, null);
    }

    @Test
    public void namesOneWayToWatch() {
        assertEquals("Included", WatchText.modes(Collections.singletonList(Offer.Mode.INCLUDED)));
        assertEquals("Free with ads", WatchText.modes(Collections.singletonList(Offer.Mode.FREE_WITH_ADS)));
    }

    @Test
    public void joinsSeveralWaysWithOr() {
        assertEquals("Rent or buy", WatchText.modes(Arrays.asList(Offer.Mode.RENT, Offer.Mode.BUY)));
        assertEquals("Included, rent or buy",
                WatchText.modes(Arrays.asList(Offer.Mode.INCLUDED, Offer.Mode.RENT, Offer.Mode.BUY)));
    }

    @Test
    public void actionOpensAnInstalledAppAndOffersToGetAMissingOne() {
        assertEquals("Open", WatchText.action(option("Peacock", true, Offer.Mode.INCLUDED)));
        assertEquals("Get the app", WatchText.action(option("Netflix", false, Offer.Mode.INCLUDED)));
    }

    @Test
    public void heroLineSaysWhereYouCanWatchOnThisTv() {
        assertEquals("Included with Peacock", WatchText.watchHere(option("Peacock", true, Offer.Mode.INCLUDED)));
        assertEquals("Free with ads on Tubi", WatchText.watchHere(option("Tubi", true, Offer.Mode.FREE_WITH_ADS)));
        assertEquals("Free on Pluto TV", WatchText.watchHere(option("Pluto TV", true, Offer.Mode.FREE)));
    }

    @Test
    public void factsListScoreYearRatingLengthAndTwoGenres() {
        Details d = new Details("PG-13", 7.8, "2h 35m", Arrays.asList("Science Fiction", "Adventure", "Drama"),
                "", "", null, Collections.<Offer>emptyList());
        Title t = new Title(Title.Kind.MOVIE, 1, "Dune", "2021", null, null, d);

        assertEquals(Arrays.asList("★ 7.8", "2021", "PG-13", "2h 35m", "Science Fiction, Adventure"),
                WatchText.facts(t));
    }

    @Test
    public void factsCanLeaveOutGenresWhenTheScreenShowsThemApart() {
        Details d = new Details("PG-13", 7.8, "2h 35m", Arrays.asList("Drama"), "", "", null,
                Collections.<Offer>emptyList());
        Title t = new Title(Title.Kind.MOVIE, 1, "Dune", "2021", null, null, d);

        assertEquals(Arrays.asList("★ 7.8", "2021", "PG-13", "2h 35m"), WatchText.facts(t, false));
    }

    @Test
    public void factsLeaveOutWhatIsMissing() {
        Details d = new Details("", 0, "", Collections.<String>emptyList(), "", "", null,
                Collections.<Offer>emptyList());
        Title t = new Title(Title.Kind.TV, 2, "New", "", null, "/p", d);

        assertTrue(WatchText.facts(t).isEmpty());
    }

    @Test
    public void factsForATitleWithoutDetailsAreTheYearOnly() {
        Title t = new Title(Title.Kind.TV, 3, "Show", "2024", null, "/p");

        List<String> facts = WatchText.facts(t);

        assertEquals(Collections.singletonList("2024"), facts);
    }

    @Test
    public void listsNamesWithCommasThenAnd() {
        assertEquals("", WatchText.list(Collections.<String>emptyList()));
        assertEquals("Peacock", WatchText.list(Arrays.asList("Peacock")));
        assertEquals("Peacock and Apple TV", WatchText.list(Arrays.asList("Peacock", "Apple TV")));
        assertEquals("Comedy, Drama and War", WatchText.list(Arrays.asList("Comedy", "Drama", "War")));
    }

    @Test
    public void genresNoteNamesUpToThreeThenCountsTheRest() {
        assertEquals("Comedy and War", WatchText.genres(java.util.EnumSet.of(Genre.WAR, Genre.COMEDY)));
        assertEquals("Action, Comedy, Drama and 2 more", WatchText.genres(java.util.EnumSet.of(
                Genre.ACTION, Genre.COMEDY, Genre.DRAMA, Genre.HORROR, Genre.WAR)));
    }

    @Test
    public void genresNoteCountsFromTheFourthGenre() {
        assertEquals("Action, Comedy and Drama",
                WatchText.genres(java.util.EnumSet.of(Genre.ACTION, Genre.COMEDY, Genre.DRAMA)));
        assertEquals("Action, Comedy, Drama and 1 more",
                WatchText.genres(java.util.EnumSet.of(Genre.ACTION, Genre.COMEDY, Genre.DRAMA, Genre.WAR)));
    }
}
