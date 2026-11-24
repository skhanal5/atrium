package dev.skhanal.atrium.system;

/** Filters system log messages without reacting to apps or settings screens. */
final class HomePress {
    private long lastPress = -1;

    boolean accept(String line, long now) {
        if (line == null || !line.startsWith("W/ContextImpl(")
                || !line.contains("com.amazon.policy.keypolicymanager.FireTVKeyPolicyManager.handleShortPressOnHome:")) {
            return false;
        }
        if (lastPress >= 0 && now - lastPress < 700) {
            return false;
        }
        lastPress = now;
        return true;
    }
}
