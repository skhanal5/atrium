package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class HomeFeedTest {

    private static final long HOUR = 3_600_000L;

    private static Title fullTitle() {
        Details d = new Details("PG-13", 7.8, "2h 35m", Arrays.asList("Drama", "Action"),
                "Paul travels to Arrakis.", "Fear is the mind-killer.", "abc123",
                Arrays.asList(new Offer(386, "Peacock Premium", Offer.Mode.INCLUDED, "/pk.jpg"),
                        new Offer(2, "Apple TV Store", Offer.Mode.RENT, null)));
        return new Title(Title.Kind.MOVIE, 438631, "Dune", "2021", "/b.jpg", "/p.jpg", d);
    }

    private static HomeFeed feed(long savedAt, Title... trending) {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        rows.put(HomeFeed.Row.TRENDING, Arrays.asList(trending));
        return new HomeFeed(savedAt, rows);
    }

    @Test
    public void savedFeedReadsBackTheSame() throws Exception {
        HomeFeed saved = feed(1_000L, fullTitle());

        HomeFeed read = HomeFeed.fromJson(saved.toJson());

        assertEquals(1_000L, read.savedAt);
        Title t = read.rows.get(HomeFeed.Row.TRENDING).get(0);
        assertEquals("movie-438631", t.id);
        assertEquals("Dune", t.name);
        assertEquals("2021", t.year);
        assertEquals("/b.jpg", t.backdrop);
        assertEquals("/p.jpg", t.poster);
        assertEquals("PG-13", t.details.rating);
        assertEquals(7.8, t.details.score, 0);
        assertEquals("2h 35m", t.details.length);
        assertEquals(Arrays.asList("Drama", "Action"), t.details.genres);
        assertEquals("Paul travels to Arrakis.", t.details.overview);
        assertEquals("Fear is the mind-killer.", t.details.tagline);
        assertEquals("abc123", t.details.trailer);
        assertEquals(2, t.details.offers.size());
        Offer o = t.details.offers.get(1);
        assertEquals(2, o.providerId);
        assertEquals("Apple TV Store", o.provider);
        assertEquals(Offer.Mode.RENT, o.mode);
        assertNull(o.logo);
    }

    @Test
    public void savedFeedKeepsATitleWithNoDetailsOrPictures() throws Exception {
        Title bare = new Title(Title.Kind.TV, 7, "Bare", "", null, "/p.jpg");

        Title read = HomeFeed.fromJson(feed(0, bare).toJson()).rows.get(HomeFeed.Row.TRENDING).get(0);

        assertEquals(Title.Kind.TV, read.kind);
        assertNull(read.backdrop);
        assertNull(read.details);
    }

    @Test
    public void savedFeedKeepsRowOrderAndEmptyRows() throws Exception {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        rows.put(HomeFeed.Row.POPULAR_SHOWS, Collections.<Title>emptyList());
        rows.put(HomeFeed.Row.TRENDING, Collections.singletonList(fullTitle()));

        HomeFeed read = HomeFeed.fromJson(new HomeFeed(0, rows).toJson());

        assertEquals(Arrays.asList(HomeFeed.Row.POPULAR_SHOWS, HomeFeed.Row.TRENDING),
                Arrays.asList(read.rows.keySet().toArray()));
        assertTrue(read.rows.get(HomeFeed.Row.POPULAR_SHOWS).isEmpty());
    }

    @Test
    public void savedFeedKeepsTheServicesItWasFetchedFor() throws Exception {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        HomeFeed saved = new HomeFeed(1L, rows, new java.util.HashSet<>(Arrays.asList(386, 350)));

        assertEquals(new java.util.HashSet<>(Arrays.asList(386, 350)), HomeFeed.fromJson(saved.toJson()).services);
    }

    @Test
    public void savedFeedKeepsTheGenresItWasFetchedFor() throws Exception {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        HomeFeed saved = new HomeFeed(1L, rows, Collections.<Integer>emptySet(),
                java.util.EnumSet.of(Genre.COMEDY, Genre.WAR));

        assertEquals(java.util.EnumSet.of(Genre.COMEDY, Genre.WAR), HomeFeed.fromJson(saved.toJson()).genres);
    }

    @Test
    public void feedSavedBeforeGenresWereRecordedHasNone() {
        assertTrue(HomeFeed.fromJson("{\"savedAt\": 5, \"rows\": [], \"services\": [386]}").genres.isEmpty());
    }

    @Test
    public void feedSavedBeforeServicesWereRecordedHasNone() {
        // Files written by the first version have no "services" field.
        assertTrue(HomeFeed.fromJson("{\"savedAt\": 5, \"rows\": []}").services.isEmpty());
    }

    @Test
    public void damagedSavedFeedReadsAsNothing() {
        assertNull(HomeFeed.fromJson("{\"savedAt\": 5, \"rows\": [{\"row\""));
        assertNull(HomeFeed.fromJson(""));
        assertNull(HomeFeed.fromJson(null));
    }

    @Test
    public void savedFeedWithAnUnknownRowSkipsThatRow() {
        // A row removed in a later version must not break an older saved file.
        String json = "{\"savedAt\": 5, \"rows\": [{\"row\": \"GONE\", \"titles\": []}]}";

        assertTrue(HomeFeed.fromJson(json).rows.isEmpty());
    }

    @Test
    public void feedIsFreshForSixHours() {
        HomeFeed f = feed(10 * HOUR);

        assertTrue(f.isFresh(10 * HOUR));
        assertTrue(f.isFresh(16 * HOUR - 1));
        assertFalse(f.isFresh(16 * HOUR));
    }

    @Test
    public void feedSavedInTheFutureIsNotFresh() {
        // The TV clock can jump back after a reboot, before it syncs.
        assertFalse(feed(20 * HOUR).isFresh(10 * HOUR));
    }

    @Test
    public void findsATitleInAnyRow() {
        Map<HomeFeed.Row, List<Title>> rows = new LinkedHashMap<>();
        rows.put(HomeFeed.Row.TRENDING, Collections.<Title>emptyList());
        rows.put(HomeFeed.Row.ON_YOUR_APPS, Collections.singletonList(fullTitle()));
        HomeFeed f = new HomeFeed(0, rows);

        assertEquals("Dune", f.find("movie-438631").name);
        assertNull(f.find("movie-1"));
    }
}
