package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TmdbImagesTest {

    @Test
    public void picksTheSmallestBackdropThatCoversTheWidth() {
        assertEquals("https://image.tmdb.org/t/p/w300/a.jpg", TmdbImages.backdrop("/a.jpg", 280));
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", TmdbImages.backdrop("/a.jpg", 400));
        assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", TmdbImages.backdrop("/a.jpg", 1920));
    }

    @Test
    public void picksAPosterSize() {
        assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", TmdbImages.poster("/p.jpg", 180));
        assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", TmdbImages.poster("/p.jpg", 196));
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", TmdbImages.poster("/p.jpg", 210));
    }

    @Test
    public void picksALogoSize() {
        assertEquals("https://image.tmdb.org/t/p/w92/l.jpg", TmdbImages.logo("/l.jpg", 66));
    }

    @Test
    public void allowsATenPercentStretchBeforeTakingTheNextSize() {
        // 300 drawn at 330 is a 10% stretch, which nobody sees from a sofa.
        assertEquals("https://image.tmdb.org/t/p/w300/a.jpg", TmdbImages.backdrop("/a.jpg", 330));
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", TmdbImages.backdrop("/a.jpg", 334));
    }

    @Test
    public void noUrlWithoutAPath() {
        assertNull(TmdbImages.backdrop(null, 400));
        assertNull(TmdbImages.poster("", 200));
    }

    @Test
    public void shrinksByHalvesWhileTheImageStaysBigEnough() {
        assertEquals(1, TmdbImages.sampleSize(300, 169, 400, 225));
        assertEquals(2, TmdbImages.sampleSize(780, 439, 400, 225));   // 390 wide is within 10%
        assertEquals(4, TmdbImages.sampleSize(1280, 720, 320, 180));
        assertEquals(1, TmdbImages.sampleSize(1280, 720, 1920, 1080));
    }

    @Test
    public void shrinkingIsLimitedByTheTighterSide() {
        // Wide enough to halve, but not tall enough.
        assertEquals(1, TmdbImages.sampleSize(800, 300, 400, 290));
    }

    @Test
    public void unknownSizesAreNotShrunk() {
        assertEquals(1, TmdbImages.sampleSize(0, 0, 400, 225));
        assertEquals(1, TmdbImages.sampleSize(780, 439, 0, 0));
    }
}
