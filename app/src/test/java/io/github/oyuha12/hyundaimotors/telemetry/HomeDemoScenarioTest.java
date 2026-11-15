package io.github.oyuha12.hyundaimotors.telemetry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.oyuha12.hyundaimotors.R;
import io.github.oyuha12.hyundaimotors.demo.HomeDemo;
import io.github.oyuha12.hyundaimotors.diagnosis.EmergencyType;
import io.github.oyuha12.hyundaimotors.drowsiness.DrivingWarningMonitor;
import io.github.oyuha12.hyundaimotors.telemetry.SimulatedTelemetrySource.Scenario;
import org.junit.Test;

/** The home screen demo: cruising speed, and the 급발진 and 화재 build-ups. */
public class HomeDemoScenarioTest {
    private static final long BUILD_UP = HomeDemo.BUILD_UP_MILLIS;

    private static VehicleSnapshot at(Scenario scenario, float base, long t, long stopAt) {
        return SimulatedTelemetrySource.snapshotAt(scenario, t, 0L, t, null, stopAt, base);
    }

    private static VehicleSnapshot at(Scenario scenario, float base, long t) {
        return at(scenario, base, t, SimulatedTelemetrySource.NOT_STOPPING);
    }

    @Test
    public void cruise_easesFromParkedToAbout60() {
        assertEquals(0f, at(Scenario.DEMO_CRUISE, 0f, 0L).speedKph, 0.01f);
        for (long t = 10_000L; t <= 120_000L; t += 1_000L) {
            float speed = at(Scenario.DEMO_CRUISE, 0f, t).speedKph;
            assertTrue("speed " + speed + " at " + t, speed >= 55f && speed <= 65f);
        }
    }

    @Test
    public void cruise_keepsChangingALittle() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float previous = at(Scenario.DEMO_CRUISE, 0f, 10_000L).speedKph;
        for (long t = 10_250L; t <= 40_000L; t += 250L) {
            float speed = at(Scenario.DEMO_CRUISE, 0f, t).speedKph;
            // Lively but smooth: no jumps between quarter-second readings.
            assertTrue(Math.abs(speed - previous) < 2f);
            previous = speed;
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }
        assertTrue("range " + (max - min), max - min >= 3f);
    }

    @Test
    public void cruise_neverLooksLikeAPedalMistakeOrAnEmergency() {
        DrivingWarningMonitor warnings = new DrivingWarningMonitor();
        io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector detector =
                new io.github.oyuha12.hyundaimotors.diagnosis.EmergencyDetector();
        for (long t = 0L; t <= 60_000L; t += 250L) {
            VehicleSnapshot reading = at(Scenario.DEMO_CRUISE, 0f, t);
            assertNull("warning at " + t, warnings.update(reading));
            assertNull("detection at " + t, detector.evaluate(reading));
        }
    }

    @Test
    public void acceleration_climbsSteeplyBeforeTheQuestion() {
        float start = at(Scenario.DEMO_ACCELERATION, 60f, 0L).speedKph;
        float atQuestion = at(Scenario.DEMO_ACCELERATION, 60f, BUILD_UP).speedKph;
        assertEquals(60f, start, 0.01f);
        assertTrue("speed at the question " + atQuestion, atQuestion >= 100f);
        // From a standstill it still climbs well above a normal pace.
        assertTrue(at(Scenario.DEMO_ACCELERATION, 0f, 10_000L).speedKph >= 100f);
    }

    @Test
    public void acceleration_pullsOverWhenAsked() {
        long stopAt = 5_000L;
        VehicleSnapshot end = at(Scenario.DEMO_ACCELERATION, 60f, 30_000L, stopAt);
        assertEquals(0f, end.speedKph, 0.01f);
        assertEquals(VehicleSnapshot.Gear.PARK, end.gear);
    }

    @Test
    public void fire_heatsTheCabinFastWhileTheCarKeepsGoing() {
        assertEquals(24f, at(Scenario.DEMO_FIRE, 60f, 0L).cabinTemperatureC, 0.01f);
        float atQuestion = at(Scenario.DEMO_FIRE, 60f, BUILD_UP).cabinTemperatureC;
        assertTrue("cabin at the question " + atQuestion, atQuestion >= 50f);
        float speed = at(Scenario.DEMO_FIRE, 60f, BUILD_UP).speedKph;
        assertTrue("speed " + speed, speed > 50f && speed < 70f);
        assertEquals(0f, at(Scenario.DEMO_FIRE, 0f, BUILD_UP).speedKph, 0.01f); // Parked
    }

    @Test
    public void fire_pullsOverWhenAskedAndStaysHot() {
        VehicleSnapshot end = at(Scenario.DEMO_FIRE, 60f, 30_000L, 5_000L);
        assertEquals(0f, end.speedKph, 0.01f);
        assertEquals(SimulatedTelemetrySource.DEMO_CABIN_PEAK_C, end.cabinTemperatureC, 0.01f);
    }

    @Test
    public void buttons_buildUpOnlyForAccelerationAndFire() {
        assertEquals(Scenario.DEMO_ACCELERATION,
                HomeDemo.buildUp(EmergencyType.UNINTENDED_ACCELERATION, false));
        assertEquals(Scenario.DEMO_FIRE, HomeDemo.buildUp(EmergencyType.FIRE, false));
        assertNull(HomeDemo.buildUp(EmergencyType.ACCIDENT, false));
        assertNull(HomeDemo.buildUp(EmergencyType.FLOOD, false));
        // Video mode keeps its own scenarios.
        assertNull(HomeDemo.buildUp(EmergencyType.UNINTENDED_ACCELERATION, true));
        assertNull(HomeDemo.buildUp(EmergencyType.FIRE, true));
    }

    @Test
    public void buttons_sayWhatBuiltUp() {
        assertEquals(R.string.reason_demo_acceleration,
                HomeDemo.reason(EmergencyType.UNINTENDED_ACCELERATION));
        assertEquals(R.string.reason_cabin_heat, HomeDemo.reason(EmergencyType.FIRE));
        assertEquals(R.string.reason_manual, HomeDemo.reason(EmergencyType.FLOOD));
        assertTrue(HomeDemo.isBuildUp(Scenario.DEMO_FIRE));
        assertNotEquals(true, HomeDemo.isBuildUp(Scenario.DEMO_CRUISE));
    }
}
