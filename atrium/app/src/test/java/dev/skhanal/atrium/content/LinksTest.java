package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LinksTest {

    @Test
    public void trailerIsAYouTubeWatchLink() {
        assertEquals("https://www.youtube.com/watch?v=Way9Dexny3w", Links.trailer("Way9Dexny3w"));
    }

    @Test
    public void trailerIsNullWithNoVideo() {
        assertNull(Links.trailer(null));
        assertNull(Links.trailer(""));
    }

    @Test
    public void appstorePageOpensOneAppByPackage() {
        assertEquals("amzn://apps/android?p=com.hulu.plus", Links.appstorePage("com.hulu.plus"));
    }

    @Test
    public void appstorePageIsNullWithoutAPackage() {
        assertNull(Links.appstorePage(null));
        assertNull(Links.appstorePage(""));
    }
}
