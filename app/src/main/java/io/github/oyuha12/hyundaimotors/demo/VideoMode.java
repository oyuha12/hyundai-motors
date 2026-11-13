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
     * emulator's Location setting is what the video shows, or the filming location before the
     * first fix. Outside video mode null, which keeps the sample position simulations always used.
     */
    @Nullable
    public static GeoPoint simulationPosition(boolean videoMode, @Nullable GeoPoint sdkPosition) {
        if (!videoMode) return null;
        return sdkPosition != null ? sdkPosition : VideoConfig.FILMING.position();
    }

    /**
     * Where distances are measured from when no position is known: in video mode the filming
     * location once it is set, otherwise the sample position.
     */
    public static GeoPoint fallbackPosition(boolean videoMode) {
        GeoPoint filming = VideoConfig.FILMING.position();
        return videoMode && filming != null ? filming : VideoConfig.FALLBACK_POSITION;
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

    /** Whether to send the evacuation route now: video mode, with the menu set to this moment. */
    public static boolean autoRoutesAt(boolean videoMode, AutoRoute setting, AutoRoute moment) {
        return videoMode && moment != AutoRoute.OFF && setting == moment;
    }

    /** The in-app route screen joins the navigation request only in video mode, when chosen. */
    public static boolean showsFallbackRoute(boolean videoMode, boolean fallbackSetting) {
        return videoMode && fallbackSetting;
    }

    /**
     * Whether the in-app route may speak now in video mode: not while the situation guidance is
     * being read, not while the report offer, the reply to it or the report announcement has the
     * voice, and not over a sentence still being read. A call held back is tried again.
     */
    public static boolean routeVoiceFree(
            boolean guidanceRunning, boolean reportBusy, boolean speaking) {
        return !guidanceRunning && !reportBusy && !speaking;
    }

    /** A stored lead-in, or the default when it is not one of the menu's choices. */
    public static int leadInSeconds(int stored) {
        return VideoConfig.LEAD_IN_SECONDS.contains(stored)
                ? stored
                : VideoConfig.DEFAULT_LEAD_IN_SECONDS;
    }
}
