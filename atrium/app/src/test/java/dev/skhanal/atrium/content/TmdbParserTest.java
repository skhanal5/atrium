package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TmdbParserTest {

    @Test
    public void listKeepsMoviesAndShowsInOrder() throws Exception {
        String json = """
                {"results": [
                  {"id": 1, "media_type": "movie", "title": "Dune", "release_date": "2021-09-15",
                   "backdrop_path": "/b1.jpg", "poster_path": "/p1.jpg"},
                  {"id": 2, "media_type": "tv", "name": "Slow Horses", "first_air_date": "2022-04-01",
                   "backdrop_path": "/b2.jpg", "poster_path": "/p2.jpg"}
                ]}""";

        List<Title> titles = TmdbParser.list(json, null);

        assertEquals(2, titles.size());
        Title dune = titles.get(0);
        assertEquals("movie-1", dune.id);
        assertEquals(Title.Kind.MOVIE, dune.kind);
        assertEquals("Dune", dune.name);
        assertEquals("2021", dune.year);
        assertEquals("/b1.jpg", dune.backdrop);
        assertEquals("/p1.jpg", dune.poster);
        Title horses = titles.get(1);
        assertEquals("tv-2", horses.id);
        assertEquals(Title.Kind.TV, horses.kind);
        assertEquals("Slow Horses", horses.name);
        assertEquals("2022", horses.year);
    }

    @Test
    public void listSkipsPeopleAndTitlesWithNoArtwork() throws Exception {
        String json = """
                {"results": [
                  {"id": 3, "media_type": "person", "name": "Zendaya", "profile_path": "/z.jpg"},
                  {"id": 4, "media_type": "movie", "title": "No Art"},
                  {"id": 5, "media_type": "movie", "title": "Has Art", "backdrop_path": "/b5.jpg"}
                ]}""";

        List<Title> titles = TmdbParser.list(json, null);

        assertEquals(1, titles.size());
        assertEquals("Has Art", titles.get(0).name);
    }

    @Test
    public void listUsesTheGivenKindWhenTheResponseHasNone() throws Exception {
        // Discover and popular lists do not say what kind each item is.
        String json = """
                {"results": [{"id": 6, "name": "Severance", "poster_path": "/p6.jpg"}]}""";

        List<Title> titles = TmdbParser.list(json, Title.Kind.TV);

        assertEquals("tv-6", titles.get(0).id);
        assertEquals("Severance", titles.get(0).name);
    }

    @Test
    public void listIsEmptyWhenThereAreNoResults() throws Exception {
        assertTrue(TmdbParser.list("{\"results\": []}", null).isEmpty());
        assertTrue(TmdbParser.list("{}", null).isEmpty());
    }

    @Test(expected = org.json.JSONException.class)
    public void listRejectsTextThatIsNotJson() throws Exception {
        TmdbParser.list("<html>Service unavailable</html>", null);
    }

    private static final String MOVIE_DETAILS = """
            {"id": 438631, "title": "Dune", "release_date": "2021-09-15",
             "backdrop_path": "/b.jpg", "poster_path": "/p.jpg",
             "vote_average": 7.789, "runtime": 155,
             "tagline": "Beyond fear, destiny awaits.",
             "overview": "Paul Atreides travels to Arrakis.",
             "genres": [{"name": "Science Fiction"}, {"name": "Adventure"}, {"name": "Drama"}, {"name": "Action"}],
             "release_dates": {"results": [
               {"iso_3166_1": "GB", "release_dates": [{"certification": "12A"}]},
               {"iso_3166_1": "US", "release_dates": [{"certification": ""}, {"certification": "PG-13"}]}
             ]},
             "videos": {"results": [
               {"site": "YouTube", "type": "Teaser", "key": "teaser", "official": true},
               {"site": "Vimeo", "type": "Trailer", "key": "vimeo", "official": true},
               {"site": "YouTube", "type": "Trailer", "key": "fan", "official": false},
               {"site": "YouTube", "type": "Trailer", "key": "official", "official": true}
             ]}}""";

    @Test
    public void detailsReadsTheFactsAboutAMovie() throws Exception {
        Details d = TmdbParser.details(MOVIE_DETAILS, Title.Kind.MOVIE, "US").details;

        assertEquals("PG-13", d.rating);
        assertEquals(7.8, d.score, 0.001);
        assertEquals("2h 35m", d.length);
        assertEquals(List.of("Science Fiction", "Adventure", "Drama"), d.genres);
        assertEquals("Paul Atreides travels to Arrakis.", d.overview);
        assertEquals("Beyond fear, destiny awaits.", d.tagline);
    }

    @Test
    public void detailsKeepsTheListFields() throws Exception {
        Title t = TmdbParser.details(MOVIE_DETAILS, Title.Kind.MOVIE, "US");

        assertEquals("movie-438631", t.id);
        assertEquals("Dune", t.name);
        assertEquals("2021", t.year);
        assertEquals("/b.jpg", t.backdrop);
    }

    @Test
    public void detailsPicksTheOfficialYouTubeTrailer() throws Exception {
        assertEquals("official", TmdbParser.details(MOVIE_DETAILS, Title.Kind.MOVIE, "US").details.trailer);
    }

    @Test
    public void detailsReadsAShow() throws Exception {
        String json = """
                {"id": 100, "name": "Slow Horses", "first_air_date": "2022-04-01",
                 "poster_path": "/p.jpg", "number_of_seasons": 5, "vote_average": 8.2,
                 "content_ratings": {"results": [{"iso_3166_1": "US", "rating": "TV-MA"}]}}""";

        Details d = TmdbParser.details(json, Title.Kind.TV, "US").details;

        assertEquals("TV-MA", d.rating);
        assertEquals("5 seasons", d.length);
    }

    @Test
    public void detailsSaysOneSeasonNotOneSeasons() throws Exception {
        String json = "{\"id\": 1, \"name\": \"New\", \"poster_path\": \"/p.jpg\", \"number_of_seasons\": 1}";

        assertEquals("1 season", TmdbParser.details(json, Title.Kind.TV, "US").details.length);
    }

    @Test
    public void detailsLeavesMissingFactsEmpty() throws Exception {
        // A new or obscure title often has no rating, no trailer and no runtime.
        String json = "{\"id\": 2, \"title\": \"Unknown\", \"poster_path\": \"/p.jpg\"}";

        Details d = TmdbParser.details(json, Title.Kind.MOVIE, "US").details;

        assertEquals("", d.rating);
        assertEquals(0, d.score, 0);
        assertEquals("", d.length);
        assertTrue(d.genres.isEmpty());
        assertEquals("", d.overview);
        assertEquals("", d.tagline);
        assertEquals(null, d.trailer);
    }

    @Test
    public void detailsReadsWhereToWatchInTheCountryInModeOrder() throws Exception {
        String json = """
                {"id": 4, "title": "Film", "poster_path": "/p.jpg",
                 "watch/providers": {"results": {
                   "GB": {"flatrate": [{"provider_id": 8, "provider_name": "Netflix", "logo_path": "/n.jpg"}]},
                   "US": {
                     "buy": [{"provider_id": 2, "provider_name": "Apple TV Store", "logo_path": "/a.jpg"}],
                     "rent": [{"provider_id": 2, "provider_name": "Apple TV Store", "logo_path": "/a.jpg"}],
                     "ads": [{"provider_id": 73, "provider_name": "Tubi TV", "logo_path": "/t.jpg"}],
                     "flatrate": [{"provider_id": 386, "provider_name": "Peacock Premium", "logo_path": "/pk.jpg"}]
                   }}}}""";

        List<Offer> offers = TmdbParser.details(json, Title.Kind.MOVIE, "US").details.offers;

        assertEquals(4, offers.size());
        assertOffer(offers.get(0), 386, "Peacock Premium", Offer.Mode.INCLUDED, "/pk.jpg");
        assertOffer(offers.get(1), 73, "Tubi TV", Offer.Mode.FREE_WITH_ADS, "/t.jpg");
        assertOffer(offers.get(2), 2, "Apple TV Store", Offer.Mode.RENT, "/a.jpg");
        assertOffer(offers.get(3), 2, "Apple TV Store", Offer.Mode.BUY, "/a.jpg");
    }

    @Test
    public void detailsHasNoOffersWhenTheCountryIsNotListed() throws Exception {
        String json = """
                {"id": 5, "title": "Film", "poster_path": "/p.jpg",
                 "watch/providers": {"results": {"GB": {"flatrate": [
                   {"provider_id": 8, "provider_name": "Netflix"}]}}}}""";

        assertTrue(TmdbParser.details(json, Title.Kind.MOVIE, "US").details.offers.isEmpty());
    }

    private static void assertOffer(Offer o, int id, String name, Offer.Mode mode, String logo) {
        assertEquals(id, o.providerId);
        assertEquals(name, o.provider);
        assertEquals(mode, o.mode);
        assertEquals(logo, o.logo);
    }

    @Test
    public void detailsShowsMinutesOnlyForAShortFilm() throws Exception {
        String json = "{\"id\": 3, \"title\": \"Short\", \"poster_path\": \"/p.jpg\", \"runtime\": 45}";

        assertEquals("45m", TmdbParser.details(json, Title.Kind.MOVIE, "US").details.length);
    }
}
