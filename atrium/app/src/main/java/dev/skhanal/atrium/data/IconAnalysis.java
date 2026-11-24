package dev.skhanal.atrium.data;

/** What one pass over an app icon's pixels told us. */
public final class IconAnalysis {

    /** The colour the room should take when this app is focused, or 0 for none. */
    public final int accent;

    /**
     * True when the icon is mostly transparent around its edge, which means it
     * is a bare logo rather than a full square. Those need a surface drawn
     * behind them or they float.
     */
    public final boolean needsPlate;

    public IconAnalysis(int accent, boolean needsPlate) {
        this.accent = accent;
        this.needsPlate = needsPlate;
    }
}
