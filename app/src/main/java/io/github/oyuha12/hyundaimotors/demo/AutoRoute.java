package io.github.oyuha12.hyundaimotors.demo;

/** Video mode: when to send the evacuation route to the navigation app without a tap. */
public enum AutoRoute {
    /** Only when "경로 안내" is pressed. */
    OFF,
    /** Once the report has been announced, as the status screen opens. */
    AFTER_REPORT,
    /** As soon as the situation is confirmed and the guidance starts. */
    ON_CONFIRM;

    /** A stored choice, or OFF when it is not one. */
    public static AutoRoute fromStored(int ordinal) {
        AutoRoute[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OFF;
    }
}
