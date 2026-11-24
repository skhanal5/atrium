package dev.skhanal.atrium.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FocusGridTest {

    private static void at(FocusGrid g, int row, int col) {
        assertEquals("row", row, g.row());
        assertEquals("column", col, g.column());
    }

    @Test
    public void leftAndRightMoveInsideTheRowAndStopAtTheEnds() {
        FocusGrid g = new FocusGrid(3);
        g.focus(0, 0);

        assertTrue(g.move(0, 1));
        assertTrue(g.move(0, 1));
        assertFalse(g.move(0, 1));
        at(g, 0, 2);
        assertTrue(g.move(0, -1));
        at(g, 0, 1);
    }

    @Test
    public void downAndUpReturnToWhereYouWereInEachRow() {
        FocusGrid g = new FocusGrid(2, 8, 12);
        g.focus(1, 5);
        g.move(1, 0);          // into row 2, nothing remembered there yet
        at(g, 2, 0);
        g.move(0, 1);
        g.move(0, 1);
        g.move(-1, 0);         // back to row 1
        at(g, 1, 5);
        g.move(1, 0);          // back to row 2
        at(g, 2, 2);
    }

    @Test
    public void upAndDownSkipEmptyRows() {
        FocusGrid g = new FocusGrid(2, 0, 4);
        g.focus(0, 1);

        assertTrue(g.move(1, 0));
        at(g, 2, 0);
        assertTrue(g.move(-1, 0));
        at(g, 0, 1);
    }

    @Test
    public void doesNotMovePastTheFirstOrLastRow() {
        FocusGrid g = new FocusGrid(2, 3);
        g.focus(0, 0);

        assertFalse(g.move(-1, 0));
        g.move(1, 0);
        assertFalse(g.move(1, 0));
        at(g, 1, 0);
    }

    @Test
    public void aRememberedColumnPastTheEndOfAShorterRowLandsOnItsLastItem() {
        FocusGrid g = new FocusGrid(2, 10);
        g.focus(1, 9);
        g.setRows(2, 4);       // the row lost items, for example an app was hidden

        at(g, 1, 3);
        g.move(-1, 0);
        g.move(1, 0);
        at(g, 1, 3);
    }

    @Test
    public void focusMovesToTheNearestRowWhenItsRowEmpties() {
        FocusGrid g = new FocusGrid(2, 3, 5);
        g.focus(1, 2);

        g.setRows(2, 0, 5);

        at(g, 2, 0);
    }

    @Test
    public void nothingMovesWhenEveryRowIsEmpty() {
        FocusGrid g = new FocusGrid(0, 0);

        assertFalse(g.move(1, 0));
        assertFalse(g.move(0, 1));
        assertEquals(-1, g.row());
    }

    @Test
    public void focusClampsToTheGrid() {
        FocusGrid g = new FocusGrid(2, 3);

        g.focus(5, 9);

        at(g, 1, 2);
    }
}
