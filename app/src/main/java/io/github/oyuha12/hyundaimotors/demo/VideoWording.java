package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.StringRes;
import io.github.oyuha12.hyundaimotors.R;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Natural wording for video mode, in place of wording that says the app is a test.
 *
 * <p>Only the words change. Reports still go to MockEmergencyReporter and reach no one.
 */
public final class VideoWording {
    private VideoWording() {}

    private static final Map<Integer, Integer> REPLACEMENTS = new HashMap<>();

    static {
        REPLACEMENTS.put(R.string.emergency_report, R.string.video_screen_emergency_report);
        REPLACEMENTS.put(R.string.mock_report_done, R.string.video_screen_report_done);
        REPLACEMENTS.put(R.string.dispatch_request, R.string.video_screen_dispatch_request);
        REPLACEMENTS.put(R.string.dispatch_requested, R.string.video_screen_dispatch_requested);
        REPLACEMENTS.put(R.string.mock_dispatch_done, R.string.video_screen_dispatch_done);
        REPLACEMENTS.put(R.string.responders_notice, R.string.video_screen_responders_notice);
        REPLACEMENTS.put(R.string.detail_reports_label, R.string.video_screen_reports_label);
        REPLACEMENTS.put(R.string.step_health_report, R.string.video_screen_step_health_report);
    }

    /** The text to show: in video mode, the natural replacement if there is one. */
    @StringRes
    public static int pick(@StringRes int normal, boolean videoMode) {
        if (!videoMode) return normal;
        Integer replacement = REPLACEMENTS.get(normal);
        return replacement == null ? normal : replacement;
    }

    /** Strings that video mode replaces. */
    static Set<Integer> replaced() {
        return Collections.unmodifiableSet(REPLACEMENTS.keySet());
    }
}
