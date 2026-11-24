package dev.skhanal.atrium.ui;

import java.util.Arrays;

/**
 * Where focus is on a screen of rows, and where each remote press moves it.
 *
 * Android's own focus search picks the nearest view in the direction pressed.
 * On a screen of scrolling rows that is rarely what a person means: going up
 * and coming back down should land on the card you left, not the one that
 * happens to sit under the button above. So the screen keeps this model and
 * moves focus itself.
 */
public final class FocusGrid {

    private int[] sizes;
    /** The column last used in each row, or -1 when the row was never visited. */
    private int[] memory;
    private int row = -1;
    private int column = -1;

    /** @param sizes how many items each row has, top to bottom. A row may be empty. */
    public FocusGrid(int... sizes) {
        this.sizes = sizes.clone();
        this.memory = new int[sizes.length];
        Arrays.fill(memory, -1);
    }

    public int row() {
        return row;
    }

    public int column() {
        return column;
    }

    /**
     * Change the row sizes, for example after an app is hidden. Focus stays
     * where it was when it still can, and moves to the nearest row when its
     * own row is now empty.
     */
    public void setRows(int... newSizes) {
        int[] oldMemory = memory;
        sizes = newSizes.clone();
        memory = new int[sizes.length];
        Arrays.fill(memory, -1);
        System.arraycopy(oldMemory, 0, memory, 0, Math.min(oldMemory.length, memory.length));
        if (row < 0) {
            return;
        }
        if (row < sizes.length && sizes[row] > 0) {
            focus(row, column);
            return;
        }
        int nearest = nearestRow(Math.min(row, sizes.length - 1));
        if (nearest < 0) {
            row = column = -1;
        } else {
            focus(nearest, remembered(nearest));
        }
    }

    /** Put focus on one item. Out-of-range values are clamped to the grid. */
    public void focus(int r, int c) {
        int target = Math.max(0, Math.min(r, sizes.length - 1));
        if (sizes.length == 0) {
            return;
        }
        if (sizes[target] == 0) {
            target = nearestRow(target);
            if (target < 0) {
                return;
            }
        }
        row = target;
        column = Math.max(0, Math.min(c, sizes[target] - 1));
        memory[row] = column;
    }

    /**
     * Move by one row or one column.
     *
     * @return true when focus moved, false at an edge
     */
    public boolean move(int rows, int columns) {
        if (row < 0) {
            int first = nearestRow(0);
            if (first < 0) {
                return false;
            }
            focus(first, 0);
            return true;
        }
        if (columns != 0) {
            int c = column + Integer.signum(columns);
            if (c < 0 || c >= sizes[row]) {
                return false;
            }
            focus(row, c);
            return true;
        }
        int step = Integer.signum(rows);
        for (int r = row + step; r >= 0 && r < sizes.length; r += step) {
            if (sizes[r] > 0) {
                focus(r, remembered(r));
                return true;
            }
        }
        return false;
    }

    private int remembered(int r) {
        return memory[r] < 0 ? 0 : Math.min(memory[r], sizes[r] - 1);
    }

    /** The closest row with items, looking down first. -1 when every row is empty. */
    private int nearestRow(int from) {
        for (int d = 0; d < sizes.length; d++) {
            if (from + d < sizes.length && from + d >= 0 && sizes[from + d] > 0) {
                return from + d;
            }
            if (from - d >= 0 && from - d < sizes.length && sizes[from - d] > 0) {
                return from - d;
            }
        }
        return -1;
    }
}
