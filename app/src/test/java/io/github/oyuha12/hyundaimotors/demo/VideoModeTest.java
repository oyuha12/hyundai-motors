package io.github.oyuha12.hyundaimotors.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.telemetry.GeoPoint;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
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
        assertNull(VideoMode.simulationPosition(true, null));
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

    /** Places stay empty until the team gives real ones; nothing may be made up. */
    @Test
    public void places_areNotFilledInYet() {
        assertTrue(VideoConfig.PLACES.isEmpty());
    }
}
