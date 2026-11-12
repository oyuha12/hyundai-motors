package io.github.oyuha12.hyundaimotors.demo;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Settings for the demo video, kept in one place. They take effect only while video mode is on.
 *
 * <p>Places come from the team. Never fill them with made-up places or coordinates.
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
    // From the team (2026-11-12). Coordinates were converted from Google Maps plus codes. Never
    // replace them with made-up places. A place left null shows "장소 미설정" and stays out of
    // distances, countdowns and the mini map. The address is optional: it goes to the navigation
    // app with the route.

    /**
     * Filming location. The name shows on the home screen and the report receipt. The
     * coordinates stand in until the location SDK reports a position, and are what 장정인 types
     * into the emulator's Location setting. Same point as FALLBACK_POSITION.
     */
    public static final VideoPlace FILMING = new VideoPlace(
            null, "서울 강남구 도곡동 (양재역 부근)", 37.48544722, 127.03636666);

    public static final VideoPlace POLICE = new VideoPlace(
            Facility.Type.POLICE, "서초경찰서 서초2파출소", 37.4875125, 127.0289844);

    public static final VideoPlace FIRE_STATION = new VideoPlace(
            Facility.Type.FIRE_STATION, "서초소방서 양재119안전센터", 37.4694375, 127.0408281,
            "서울 서초구 동산로 9");

    /** Optional. Set SHOW_HOSPITAL to false to leave hospitals out of the video. */
    public static final VideoPlace HOSPITAL = new VideoPlace(
            Facility.Type.HOSPITAL, "강남세브란스병원 응급실", 37.4927375, 127.0462656,
            "서울 강남구 언주로 211");

    public static final boolean SHOW_HOSPITAL = true;

    // Safe stop places ("안전 정차 장소"), one per scenario.

    public static final VideoPlace SAFE_STOP_ACCELERATION = new VideoPlace(
            Facility.Type.SAFE_STOP, "양재역 공영주차장", 37.4785375, 127.0413281);

    /** Fire: an above-ground lot. A burning car must never be sent indoors or underground. */
    public static final VideoPlace SAFE_STOP_FIRE = new VideoPlace(
            Facility.Type.SAFE_STOP, "매헌윤봉길의사기념관 주차장(지상)", 37.4690125, 127.0364219);

    /** The third scenario (collision): the same lot as unintended acceleration, for now. */
    public static final VideoPlace SAFE_STOP_COLLISION = SAFE_STOP_ACCELERATION;

    /**
     * Where the car should stop in each situation the video shows. Other situations have no safe
     * stop place in the video and show "장소 미설정".
     */
    @Nullable
    public static VideoPlace safeStopFor(EmergencyType type) {
        switch (type) {
            case UNINTENDED_ACCELERATION:
                return SAFE_STOP_ACCELERATION;
            case FIRE:
                return SAFE_STOP_FIRE;
            case ACCIDENT:
                return SAFE_STOP_COLLISION;
            default:
                return null;
        }
    }

    /** The places video mode uses in place of the sample facilities, for example nearby. */
    public static final List<VideoPlace> PLACES = Collections.unmodifiableList(SHOW_HOSPITAL
            ? Arrays.asList(SAFE_STOP_ACCELERATION, SAFE_STOP_FIRE, POLICE, FIRE_STATION, HOSPITAL)
            : Arrays.asList(SAFE_STOP_ACCELERATION, SAFE_STOP_FIRE, POLICE, FIRE_STATION));

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

    /** When to send the safe stop route to the navigation app without a tap. */
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
