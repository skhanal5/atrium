package dev.skhanal.atrium.system;

/** Run with firetv/test-home.sh. No Android runtime is needed. */
public final class HomePressTest {
    private static final String HOME = "W/ContextImpl( 2162): Calling a method "
            + "com.amazon.policy.keypolicymanager.FireTVKeyPolicyManager.handleShortPressOnHome:3201";

    public static void main(String[] args) {
        HomePress press = new HomePress();
        check(press.accept(HOME, 0), "first Home press");
        check(!press.accept(HOME, 699), "duplicate event");
        check(press.accept(HOME, 700), "next Home press at the limit");
        for (String line : new String[] {null, "", "W/ContextImpl( 2): settings",
                "I/am_focused_activity( 2): [0,com.amazon.tv.launcher/.ui.HomeActivity_vNext,x]",
                HOME.replace("W/ContextImpl(", "W/OtherApp("),
                HOME.replace("handleShortPressOnHome", "handleLongPressOnHome")}) {
            check(!new HomePress().accept(line, 0), "ignore unrelated event: " + line);
        }
        System.out.println("HomePress tests passed");
    }

    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
    }
}
