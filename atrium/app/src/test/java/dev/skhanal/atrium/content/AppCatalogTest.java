package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class AppCatalogTest {

    private static List<String> names(List<TvApp> apps) {
        List<String> out = new ArrayList<>();
        for (TvApp a : apps) {
            out.add(a.name);
        }
        return out;
    }

    @Test
    public void holdsEveryStreamingAppAndTheOtherCheckedApps() {
        List<String> all = names(AppCatalog.ALL);

        for (TvApp a : StreamingApps.ALL) {
            assertTrue(a.name, all.contains(a.name));
        }
        assertTrue(all.contains("Spotify"));
        assertTrue(all.contains("ESPN"));
    }

    @Test
    public void noTwoAppsShareAnAppstorePackage() {
        Set<String> seen = new HashSet<>();
        for (TvApp a : AppCatalog.ALL) {
            assertTrue(a.appstorePackage(), seen.add(a.appstorePackage()));
        }
    }

    @Test
    public void blankSearchListsEveryAppInOrder() {
        assertEquals(names(AppCatalog.ALL), names(AppCatalog.search("  ")));
        assertEquals(names(AppCatalog.ALL), names(AppCatalog.search(null)));
    }

    @Test
    public void searchMatchesTheStartOfAnyWordInTheName() {
        assertEquals("Amazon Music", names(AppCatalog.search("mus")).get(0));
        assertEquals(List.of("Pluto TV"), names(AppCatalog.search("plu")));
    }

    @Test
    public void searchIgnoresCaseAndSymbols() {
        assertEquals(List.of("Disney+"), names(AppCatalog.search("DISNEY")));
        assertEquals(List.of("Paramount+"), names(AppCatalog.search("paramount+")));
    }

    @Test
    public void searchFindsAppsByTopic() {
        // Amazon Music matches by name, so it comes before Spotify, which matches by topic.
        assertEquals(List.of("Amazon Music", "Spotify"), names(AppCatalog.search("music")));
        assertEquals(List.of("Amazon Music", "Spotify"), names(AppCatalog.search("mus")));
        assertTrue(names(AppCatalog.search("sports")).contains("ESPN"));
        assertTrue(names(AppCatalog.search("anime")).contains("Crunchyroll"));
    }

    @Test
    public void namesThatStartWithTheSearchComeBeforeTopicMatches() {
        // Spotify's name starts with "sp". ESPN only matches its topic, "sports".
        List<String> found = names(AppCatalog.search("sp"));

        assertEquals("Spotify", found.get(0));
        assertTrue(found.indexOf("ESPN") > 0);
    }

    @Test
    public void searchWithNoMatchIsEmpty() {
        assertTrue(AppCatalog.search("zzzz").isEmpty());
        assertFalse(AppCatalog.search("netflix").isEmpty());
    }
}
