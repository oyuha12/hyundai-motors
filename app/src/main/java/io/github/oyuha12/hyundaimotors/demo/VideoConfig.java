package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Settings for the demo video, kept in one place. They take effect only while video mode is on.
 *
 * <p>Values marked TODO wait for the team. Never fill them with made-up places or coordinates.
 */
public final class VideoConfig {
    private VideoConfig() {}

    /**
     * The sample point the app already used, moved here unchanged. In video mode it is used only
     * before the location SDK (FusedLocation) reports a position: the emulator's Location setting
     * is the real source while filming. Outside video mode simulations keep using it, as before.
     */
    public static final GeoPoint FALLBACK_POSITION = new GeoPoint(37.48544722, 127.03636666);

    /**
     * Place name (address) shown on the home screen instead of coordinates.
     * TODO(영상): the address for the video. Until then the screen says the name is not set.
     */
    @Nullable public static final String LOCATION_NAME = null;

    /** A real place shown in the video. */
    public static final class Place {
        public final Facility.Type type;
        public final String name;
        public final GeoPoint position;

        public Place(Facility.Type type, String name, GeoPoint position) {
            this.type = type;
            this.name = name;
            this.position = position;
        }
    }

    /**
     * TODO(영상): real places for the video (name, type, coordinates), from the team in step 5.
     * Until then the existing sample facilities are shown.
     */
    public static final List<Place> PLACES = Collections.emptyList();

    /** Normal driving before the scenario begins, to line up with the dashcam footage. */
    public static final List<Integer> LEAD_IN_SECONDS =
            Collections.unmodifiableList(Arrays.asList(0, 3, 5, 10));

    public static final int DEFAULT_LEAD_IN_SECONDS = 0;

    /** A detection goes straight to the guidance, without "네/아니요", unless the menu says no. */
    public static final boolean DEFAULT_SKIP_CONFIRMATION = true;

    /**
     * Stand-in for a spoken "신고해 줘" when the emulator microphone does not work: a double tap
     * while a report is awaited. Off unless turned on in the menu.
     */
    public static final boolean DEFAULT_STAGED_VOICE_REPORT = false;

    /** Above this speed the car shows as driving in video mode. */
    public static final float DRIVING_SPEED_KPH = 1f;

    /** Scenarios in the hidden demo menu. The first two pull over as the guidance asks. */
    public static final Scenario SCENARIO_ACCELERATION = Scenario.VIDEO_ACCELERATION;

    public static final Scenario SCENARIO_FIRE = Scenario.VIDEO_FIRE;

    /** The existing collision scenario, with the lead-in. */
    public static final Scenario SCENARIO_THIRD = Scenario.COLLISION;
}
