package io.github.oyuha12.hyundaimotors.demo;

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

    // ---- Real places for the video -------------------------------------------------------------
    //
    // TODO(영상): fill in each name, latitude and longitude from the team. Never make them up.
    // The address is optional: it goes to the navigation app with the route.
    // Leave a value null until it is known: screens then show "장소 미설정" (or "위치 이름 미설정")
    // and leave the place out of distances, countdowns and the mini map. Once a place has a name
    // and both coordinates it shows everywhere with no other change.

    /**
     * Filming location. The name shows on the home screen and the report receipt. The
     * coordinates stand in until the location SDK reports a position, and are what 장정인 types
     * into the emulator's Location setting.
     */
    public static final VideoPlace FILMING = new VideoPlace(
            null,
            null, // TODO(영상): 촬영 위치 이름
            null, // TODO(영상): 위도
            null); // TODO(영상): 경도

    public static final VideoPlace EVACUATION = new VideoPlace(
            Facility.Type.EVACUATION_SITE,
            null, // TODO(영상): 대피 장소 이름
            null, // TODO(영상): 위도
            null, // TODO(영상): 경도
            null); // TODO(영상): 주소 (선택)

    public static final VideoPlace POLICE = new VideoPlace(
            Facility.Type.POLICE,
            null, // TODO(영상): 경찰서 이름
            null, // TODO(영상): 위도
            null, // TODO(영상): 경도
            null); // TODO(영상): 주소 (선택)

    public static final VideoPlace FIRE_STATION = new VideoPlace(
            Facility.Type.FIRE_STATION,
            null, // TODO(영상): 소방서 이름
            null, // TODO(영상): 위도
            null, // TODO(영상): 경도
            null); // TODO(영상): 주소 (선택)

    /** Optional. Set SHOW_HOSPITAL to false to leave hospitals out of the video. */
    public static final VideoPlace HOSPITAL = new VideoPlace(
            Facility.Type.HOSPITAL,
            null, // TODO(영상): 병원 이름
            null, // TODO(영상): 위도
            null, // TODO(영상): 경도
            null); // TODO(영상): 주소 (선택)

    public static final boolean SHOW_HOSPITAL = true;

    /** The places video mode uses in place of the sample facilities. */
    public static final List<VideoPlace> PLACES = SHOW_HOSPITAL
            ? Collections.unmodifiableList(Arrays.asList(EVACUATION, POLICE, FIRE_STATION, HOSPITAL))
            : Collections.unmodifiableList(Arrays.asList(EVACUATION, POLICE, FIRE_STATION));

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

    /** When to send the evacuation route to the navigation app without a tap. */
    public static final AutoRoute DEFAULT_AUTO_ROUTE = AutoRoute.OFF;

    /**
     * Also show the in-app route screen over the navigation request, for an emulator whose
     * navigation app cannot draw maps. Off unless turned on in the menu.
     */
    public static final boolean DEFAULT_FALLBACK_ROUTE = false;

    /** Above this speed the car shows as driving in video mode. */
    public static final float DRIVING_SPEED_KPH = 1f;

    /** Scenarios in the hidden demo menu. The first two pull over as the guidance asks. */
    public static final Scenario SCENARIO_ACCELERATION = Scenario.VIDEO_ACCELERATION;

    public static final Scenario SCENARIO_FIRE = Scenario.VIDEO_FIRE;

    /** The existing collision scenario, with the lead-in. */
    public static final Scenario SCENARIO_THIRD = Scenario.COLLISION;
}
