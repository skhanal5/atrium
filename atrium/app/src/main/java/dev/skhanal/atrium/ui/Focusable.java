package dev.skhanal.atrium.ui;

/**
 * Something the remote can point at.
 *
 * The screens move focus themselves with a {@link FocusGrid}, rather than
 * through Android's focus search, so each item only has to draw itself focused
 * or not and say what Select does.
 */
public interface Focusable {

    void setFocused(boolean focused);

    /** What Select does. */
    void activate();
}
