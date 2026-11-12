package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.facility.Facility;
import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class VideoModeTest {
    @Test
    public void driving_onlyAboveOneKph() {
        assertFalse(VideoMode.isDrivingBySpeed(null));
        assertFalse(VideoMode.isDrivingBySpeed(0f));
        assertFalse(VideoMode.isDrivingBySpeed(1f));
        assertTrue(VideoMode.isDrivingBySpeed(1.1f));
        assertTrue(VideoMode.isDrivingBySpeed(40f));
    }

    @Test
    public void demoLocation_neverInVideoMode() {
        assertFalse(VideoMode.usesDemoLocation(true, true));
        assertFalse(VideoMode.usesDemoLocation(true, false));
    }

    @Test
    public void demoLocation_followsTheSettingOutsideVideoMode() {
        assertTrue(VideoMode.usesDemoLocation(false, true));
        assertFalse(VideoMode.usesDemoLocation(false, false));
    }

    @Test
    public void simulationPosition_isTheSdkFixInVideoMode() {
        GeoPoint sdk = new GeoPoint(35.1, 129.0);
        assertSame(sdk, VideoMode.simulationPosition(true, sdk));
        // Before the first fix: the filming location, or none while it is not set.
        assertSame(VideoConfig.FILMING.position(), VideoMode.simulationPosition(true, null));
    }

    @Test
    public void fallbackPosition_isTheFilmingLocationOnceSet() {
        GeoPoint filming = VideoConfig.FILMING.position();
        assertSame(filming != null ? filming : VideoConfig.FALLBACK_POSITION,
                VideoMode.fallbackPosition(true));
        assertSame(VideoConfig.FALLBACK_POSITION, VideoMode.fallbackPosition(false));
    }

    /** Off, simulations keep the sample position they always used. */
    @Test
    public void simulationPosition_isUnchangedOutsideVideoMode() {
        assertNull(VideoMode.simulationPosition(false, new GeoPoint(35.1, 129.0)));
        assertEquals(37.48544722, VideoConfig.FALLBACK_POSITION.latitude, 0.0);
        assertEquals(127.03636666, VideoConfig.FALLBACK_POSITION.longitude, 0.0);
    }

    @Test
    public void skipConfirmation_onlyForDetectionsInVideoModeWithTheOptionOn() {
        assertTrue(VideoMode.skipsConfirmation(true, true, true));
        assertFalse(VideoMode.skipsConfirmation(true, true, false));
        assertFalse(VideoMode.skipsConfirmation(true, false, true));
        assertFalse(VideoMode.skipsConfirmation(false, true, true));
        assertTrue(VideoConfig.DEFAULT_SKIP_CONFIRMATION);
    }

    @Test
    public void stagedVoiceReport_onlyInVideoModeWithTheOptionOn() {
        assertTrue(VideoMode.allowsStagedVoiceReport(true, true));
        assertFalse(VideoMode.allowsStagedVoiceReport(true, false));
        assertFalse(VideoMode.allowsStagedVoiceReport(false, true));
        assertFalse(VideoConfig.DEFAULT_STAGED_VOICE_REPORT);
    }

    @Test
    public void autoRoute_onlyAtTheChosenMomentInVideoMode() {
        assertTrue(VideoMode.autoRoutesAt(true, AutoRoute.AFTER_REPORT, AutoRoute.AFTER_REPORT));
        assertFalse(VideoMode.autoRoutesAt(true, AutoRoute.AFTER_REPORT, AutoRoute.ON_CONFIRM));
        assertTrue(VideoMode.autoRoutesAt(true, AutoRoute.ON_CONFIRM, AutoRoute.ON_CONFIRM));
        assertFalse(VideoMode.autoRoutesAt(true, AutoRoute.OFF, AutoRoute.OFF));
        assertFalse(VideoMode.autoRoutesAt(false, AutoRoute.AFTER_REPORT, AutoRoute.AFTER_REPORT));
        assertEquals(AutoRoute.OFF, VideoConfig.DEFAULT_AUTO_ROUTE);
    }

    @Test
    public void autoRoute_storedChoice() {
        for (AutoRoute moment : AutoRoute.values()) {
            assertEquals(moment, AutoRoute.fromStored(moment.ordinal()));
        }
        assertEquals(AutoRoute.OFF, AutoRoute.fromStored(-1));
        assertEquals(AutoRoute.OFF, AutoRoute.fromStored(99));
    }

    /** The in-app route screen joins the navigation request only in video mode, when chosen. */
    @Test
    public void fallbackRoute_offByDefault() {
        assertTrue(VideoMode.showsFallbackRoute(true, true));
        assertFalse(VideoMode.showsFallbackRoute(true, false));
        assertFalse(VideoMode.showsFallbackRoute(false, true));
        assertFalse(VideoConfig.DEFAULT_FALLBACK_ROUTE);
    }

    @Test
    public void menuScenarios_areTheVideoOnes() {
        assertEquals(Scenario.VIDEO_ACCELERATION, VideoConfig.SCENARIO_ACCELERATION);
        assertEquals(Scenario.VIDEO_FIRE, VideoConfig.SCENARIO_FIRE);
        assertEquals(Scenario.COLLISION, VideoConfig.SCENARIO_THIRD);
    }

    @Test
    public void leadIn_keepsMenuChoices() {
        for (int seconds : VideoConfig.LEAD_IN_SECONDS) {
            assertEquals(seconds, VideoMode.leadInSeconds(seconds));
        }
    }

    @Test
    public void leadIn_fallsBackForOtherValues() {
        assertEquals(VideoConfig.DEFAULT_LEAD_IN_SECONDS, VideoMode.leadInSeconds(7));
        assertEquals(VideoConfig.DEFAULT_LEAD_IN_SECONDS, VideoMode.leadInSeconds(-1));
    }

    /**
     * Each real place is either still TODO (all null) or complete with valid coordinates, so a
     * half-filled entry cannot slip into the video. Holds before and after the team fills them.
     */
    @Test
    public void places_areUnsetOrComplete() {
        List<VideoPlace> all = new ArrayList<>(VideoConfig.PLACES);
        all.add(VideoConfig.FILMING);
        for (VideoPlace place : all) {
            String where = String.valueOf(place.type);
            if (place.latitude != null || place.longitude != null) {
                assertNotNull(where, place.latitude);
                assertNotNull(where, place.longitude);
                assertTrue(where, place.latitude >= -90 && place.latitude <= 90);
                assertTrue(where, place.longitude >= -180 && place.longitude <= 180);
            }
            if (place != VideoConfig.FILMING) {
                assertEquals(where, place.name != null, place.position() != null);
            }
        }
    }

    @Test
    public void places_coverEvacuationPoliceAndFire() {
        assertEquals(Facility.Type.EVACUATION_SITE, VideoConfig.EVACUATION.type);
        assertEquals(Facility.Type.POLICE, VideoConfig.POLICE.type);
        assertEquals(Facility.Type.FIRE_STATION, VideoConfig.FIRE_STATION.type);
        assertTrue(VideoConfig.PLACES.contains(VideoConfig.EVACUATION));
        assertTrue(VideoConfig.PLACES.contains(VideoConfig.POLICE));
        assertTrue(VideoConfig.PLACES.contains(VideoConfig.FIRE_STATION));
        assertEquals(VideoConfig.SHOW_HOSPITAL, VideoConfig.PLACES.contains(VideoConfig.HOSPITAL));
    }
}
