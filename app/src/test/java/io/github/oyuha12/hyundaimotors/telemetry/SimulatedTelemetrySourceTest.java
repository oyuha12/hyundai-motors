package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.demo.VideoConfig;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import io.github.oyuha12.hyundaimotors.telemetry.VehicleSnapshot.Gear;
import org.junit.Test;

public class SimulatedTelemetrySourceTest {
    private static final double DELTA = 1e-9;

    @Test
    public void position_isTheSdkFix() {
        GeoPoint sdk = new GeoPoint(35.1, 129.0);
        for (Scenario scenario : Scenario.values()) {
            for (long t = 0; t <= 10_000L; t += 2_500L) {
                VehicleSnapshot reading = SimulatedTelemetrySource.snapshotAt(scenario, t, 0L, t, sdk);
                assertEquals(sdk.latitude, reading.latitude, DELTA);
                assertEquals(sdk.longitude, reading.longitude, DELTA);
            }
        }
    }

    @Test
    public void position_usesTheFallbackBeforeTheFirstFix() {
        VehicleSnapshot reading =
                SimulatedTelemetrySource.snapshotAt(Scenario.COLLISION, 1_000L, 0L, 1_000L, null);
        assertEquals(VideoConfig.FALLBACK_POSITION.latitude, reading.latitude, DELTA);
        assertEquals(VideoConfig.FALLBACK_POSITION.longitude, reading.longitude, DELTA);
    }

    @Test
    public void leadIn_holdsTheFirstReading() {
        for (Scenario scenario : Scenario.values()) {
            VehicleSnapshot first = SimulatedTelemetrySource.snapshotAt(scenario, 0L, 0L);
            for (long t = 0; t <= 5_000L; t += 250L) {
                VehicleSnapshot held =
                        SimulatedTelemetrySource.snapshotAt(scenario, t, 5_000L, t, null);
                assertSameVehicle(scenario + " at " + t, first, held);
            }
        }
    }

    @Test
    public void leadIn_delaysTheScenarioUnchanged() {
        long leadIn = 3_000L;
        for (Scenario scenario : Scenario.values()) {
            for (long t = 0; t <= 8_000L; t += 250L) {
                VehicleSnapshot plain = SimulatedTelemetrySource.snapshotAt(scenario, t, t);
                VehicleSnapshot delayed = SimulatedTelemetrySource.snapshotAt(
                        scenario, t + leadIn, leadIn, t + leadIn, null);
                assertSameVehicle(scenario + " at " + t, plain, delayed);
            }
        }
    }

    /** Without a stop request the video scenarios keep going, so nothing stops by itself. */
    @Test
    public void videoScenarios_keepGoingUntilAskedToStop() {
        VehicleSnapshot acceleration =
                SimulatedTelemetrySource.snapshotAt(Scenario.VIDEO_ACCELERATION, 60_000L, 60_000L);
        assertEquals(90f, acceleration.speedKph, 0f);
        assertEquals(Boolean.TRUE, acceleration.brakePressed);
        assertEquals(Gear.DRIVE, acceleration.gear);

        VehicleSnapshot fire =
                SimulatedTelemetrySource.snapshotAt(Scenario.VIDEO_FIRE, 60_000L, 60_000L);
        assertEquals(60f, fire.speedKph, 0f);
        assertEquals(SimulatedTelemetrySource.FIRE_CABIN_PEAK_C, fire.cabinTemperatureC, 0f);
    }

    @Test
    public void acceleration_coastsInNeutralThenSlowsToParkWithTheBrakeHeld() {
        long request = 10_000L;
        float atRequest = at(Scenario.VIDEO_ACCELERATION, request, request).speedKph;
        for (long t = request; t <= request + SimulatedTelemetrySource.STOP_DELAY_MILLIS; t += 250L) {
            VehicleSnapshot reading = at(Scenario.VIDEO_ACCELERATION, t, request);
            assertEquals("at " + t, atRequest, reading.speedKph, 0f);
            assertEquals("at " + t, Gear.NEUTRAL, reading.gear);
            assertEquals("at " + t, Boolean.TRUE, reading.brakePressed);
        }
        long end = request + SimulatedTelemetrySource.STOP_DELAY_MILLIS
                + SimulatedTelemetrySource.ACCELERATION_STOP_MILLIS;
        float before = atRequest;
        for (long t = request + SimulatedTelemetrySource.STOP_DELAY_MILLIS + 250L; t < end; t += 250L) {
            float speed = at(Scenario.VIDEO_ACCELERATION, t, request).speedKph;
            assertTrue("at " + t, speed < before && speed > 0f);
            before = speed;
        }
        VehicleSnapshot stopped = at(Scenario.VIDEO_ACCELERATION, end, request);
        assertEquals(0f, stopped.speedKph, 0f);
        assertEquals(Gear.PARK, stopped.gear);
        assertEquals(Boolean.TRUE, stopped.brakePressed);
    }

    @Test
    public void fire_pullsOverAndStaysHot() {
        long request = 10_000L;
        long end = request + SimulatedTelemetrySource.STOP_DELAY_MILLIS
                + SimulatedTelemetrySource.FIRE_STOP_MILLIS;
        assertEquals(60f, at(Scenario.VIDEO_FIRE, end - SimulatedTelemetrySource.FIRE_STOP_MILLIS,
                request).speedKph, 0f);
        VehicleSnapshot stopped = at(Scenario.VIDEO_FIRE, end, request);
        assertEquals(0f, stopped.speedKph, 0f);
        assertEquals(Gear.PARK, stopped.gear);
        assertTrue(stopped.cabinTemperatureC >= 50f);
        VehicleSnapshot later = at(Scenario.VIDEO_FIRE, end + 30_000L, request);
        assertEquals(0f, later.speedKph, 0f);
        assertEquals(SimulatedTelemetrySource.FIRE_CABIN_PEAK_C, later.cabinTemperatureC, 0f);
    }

    /** Existing scenarios play exactly as before, whatever the guidance asks. */
    @Test
    public void stopRequest_isIgnoredByExistingScenarios() {
        for (Scenario scenario : Scenario.values()) {
            if (scenario == Scenario.VIDEO_ACCELERATION || scenario == Scenario.VIDEO_FIRE) continue;
            for (long t = 0; t <= 20_000L; t += 250L) {
                assertSameVehicle(scenario + " at " + t,
                        SimulatedTelemetrySource.snapshotAt(scenario, t, t),
                        at(scenario, t, 0L));
            }
        }
    }

    private static VehicleSnapshot at(Scenario scenario, long elapsedMillis, long stopRequestedAt) {
        return SimulatedTelemetrySource.snapshotAt(
                scenario, elapsedMillis, 0L, elapsedMillis, null, stopRequestedAt);
    }

    private static void assertSameVehicle(String where, VehicleSnapshot expected, VehicleSnapshot actual) {
        assertEquals(where, expected.speedKph, actual.speedKph);
        assertEquals(where, expected.brakePressed, actual.brakePressed);
        assertEquals(where, expected.gear, actual.gear);
        assertEquals(where, expected.airbagDeployed, actual.airbagDeployed);
        assertEquals(where, expected.cabinTemperatureC, actual.cabinTemperatureC);
        assertEquals(where, expected.tirePressure(0), actual.tirePressure(0));
        assertEquals(where, expected.steeringAngleDegrees, actual.steeringAngleDegrees);
    }
}
