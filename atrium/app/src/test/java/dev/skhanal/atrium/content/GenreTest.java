package dev.skhanal.atrium.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class GenreTest {

    @Test
    public void aGenreGivesItsMovieAndShowIds() {
        assertEquals(Set.of(28), Genre.movieIds(EnumSet.of(Genre.ACTION)));
        assertEquals(Set.of(10759), Genre.showIds(EnumSet.of(Genre.ACTION)));
    }

    @Test
    public void severalGenresGiveTheirIdsOnceEach() {
        // Both are "Action & Adventure" on the show side.
        assertEquals(Set.of(12, 28), Genre.movieIds(EnumSet.of(Genre.ACTION, Genre.ADVENTURE)));
        assertEquals(Set.of(10759), Genre.showIds(EnumSet.of(Genre.ACTION, Genre.ADVENTURE)));
    }

    @Test
    public void aGenreOnOneSideOnlyHasNoIdsOnTheOther() {
        assertEquals(Set.of(27), Genre.movieIds(EnumSet.of(Genre.HORROR)));
        assertEquals(Set.of(), Genre.showIds(EnumSet.of(Genre.HORROR)));
        assertEquals(Set.of(), Genre.movieIds(EnumSet.of(Genre.REALITY)));
        assertEquals(Set.of(10764), Genre.showIds(EnumSet.of(Genre.REALITY)));
    }

    @Test
    public void offersEighteenGenresFromAToZ() {
        List<String> names = new ArrayList<>();
        for (Genre g : Genre.values()) {
            names.add(g.label);
        }

        assertEquals(List.of("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary",
                "Drama", "Family", "Fantasy", "Horror", "Kids", "Mystery", "Reality", "Romance",
                "Science fiction", "Thriller", "War", "Western"), names);
    }

    @Test
    public void savedNamesReadBackSkippingOnesNoLongerOffered() {
        assertEquals(EnumSet.of(Genre.ACTION, Genre.DRAMA),
                Genre.fromNames(List.of("DRAMA", "MUSICALS", "ACTION")));
        assertEquals(EnumSet.noneOf(Genre.class), Genre.fromNames(List.<String>of()));
    }

    @Test
    public void everyGenreHasIdsOnAtLeastOneSide() {
        for (Genre g : Genre.values()) {
            assertFalse(g.label, Genre.movieIds(EnumSet.of(g)).isEmpty()
                    && Genre.showIds(EnumSet.of(g)).isEmpty());
        }
    }
}
