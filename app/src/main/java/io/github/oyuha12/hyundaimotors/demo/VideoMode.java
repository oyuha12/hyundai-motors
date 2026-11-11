package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;

/** Rules that change while video mode is on. Outside video mode the app behaves as before. */
public final class VideoMode {
    private VideoMode() {}

    /** In video mode the driving state follows the (simulated) speed instead of a manual toggle. */
    public static boolean isDrivingBySpeed(@Nullable Float speedKph) {
        return speedKph != null && speedKph > VideoConfig.DRIVING_SPEED_KPH;
    }

    /** The fixed demo position applies only outside video mode, which always uses the SDK. */
    public static boolean usesDemoLocation(boolean videoMode, boolean demoLocationSetting) {
        return !videoMode && demoLocationSetting;
    }

    /**
     * Where a simulation puts the car. In video mode, the location SDK's last fix, so the
     * emulator's Location setting is what the video shows. Outside video mode null, which keeps
     * the sample position simulations always used.
     */
    @Nullable
    public static GeoPoint simulationPosition(boolean videoMode, @Nullable GeoPoint sdkPosition) {
        return videoMode ? sdkPosition : null;
    }

    /**
     * Whether a detection skips the "네/아니요" question and its countdown and goes straight to
     * the guidance after "○○이(가) 의심됩니다". Only in video mode, only for detections, and
     * only while the menu option is on. A situation chosen by hand still asks.
     */
    public static boolean skipsConfirmation(
            boolean videoMode, boolean detected, boolean skipSetting) {
        return videoMode && detected && skipSetting;
    }

    /** The double-tap stand-in for a spoken report works only in video mode with the option on. */
    public static boolean allowsStagedVoiceReport(boolean videoMode, boolean stagedSetting) {
        return videoMode && stagedSetting;
    }

    /** A stored lead-in, or the default when it is not one of the menu's choices. */
    public static int leadInSeconds(int stored) {
        return VideoConfig.LEAD_IN_SECONDS.contains(stored)
                ? stored
                : VideoConfig.DEFAULT_LEAD_IN_SECONDS;
    }
}
